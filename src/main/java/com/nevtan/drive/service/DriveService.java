package com.nevtan.drive.service;

import com.nevtan.drive.auth.AuthenticatedUser;

import com.nevtan.drive.config.DriveProperties;
import com.nevtan.drive.dto.CreateFolderRequest;
import com.nevtan.drive.dto.DriveFileDetailsResponse;
import com.nevtan.drive.dto.DriveFileResponse;
import com.nevtan.drive.dto.DriveFolderResponse;
import com.nevtan.drive.dto.DriveTrashResponse;
import com.nevtan.drive.dto.FilePreviewMetadataResponse;
import com.nevtan.drive.dto.MoveFileRequest;
import com.nevtan.drive.dto.RenameFileRequest;
import com.nevtan.drive.dto.RenameFolderRequest;
import com.nevtan.drive.dto.StorageUsageResponse;
import com.nevtan.drive.dto.UploadFileResponse;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DriveFolder;
import com.nevtan.drive.exception.CloudStorageException;
import com.nevtan.drive.exception.FolderNotFoundException;
import com.nevtan.drive.exception.InvalidDriveRequestException;
import com.nevtan.drive.exception.StorageLimitExceededException;
import com.nevtan.drive.exception.UploadSizeExceededException;
import com.nevtan.drive.repository.DriveFileRepository;
import com.nevtan.drive.repository.DriveFolderRepository;
import com.nevtan.drive.repository.DrivePermissionRepository;
import com.nevtan.drive.repository.DriveShareLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$");
    private static final Set<String> EXACT_PREVIEW_TYPES = Set.of(
            "application/pdf",
            "application/json",
            "text/plain",
            "text/csv");

    private final DriveFileRepository fileRepository;
    private final DriveFolderRepository folderRepository;
    private final DrivePermissionRepository permissionRepository;
    private final DriveShareLinkRepository shareLinkRepository;
    private final CloudStorageService cloudStorageService;
    private final DriveProperties driveProperties;
    private final DriveAuthorizationService authorizationService;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UploadFileResponse upload(
            AuthenticatedUser currentUser,
            Long folderId,
            MultipartFile multipartFile
    ) {
        String normalizedEmail = currentUser.email();
        validateUpload(multipartFile);
        authorizationService.requireEditableFolderOrRoot(currentUser, folderId);

        long usedBytes = fileRepository.sumStoredFileSizeByUserEmail(normalizedEmail);
        long uploadSize = multipartFile.getSize();
        if (uploadSize > driveProperties.getStorageLimitBytes() - usedBytes) {
            throw new StorageLimitExceededException(
                    usedBytes, uploadSize, driveProperties.getStorageLimitBytes());
        }

        String originalFileName = sanitizeFileName(multipartFile.getOriginalFilename());
        validateBlockedExtension(originalFileName);
        String storedFileName = buildStoredFileName(originalFileName);
        String objectKey = buildObjectKey(normalizedEmail, storedFileName);
        String contentType = preserveContentType(multipartFile.getContentType());

        try (InputStream inputStream = multipartFile.getInputStream()) {
            cloudStorageService.upload(
                    objectKey,
                    inputStream,
                    uploadSize,
                    contentType);
        } catch (IOException exception) {
            throw new CloudStorageException("Could not read or upload the file", exception);
        }

        DriveFile file = DriveFile.builder()
                .userEmail(normalizedEmail)
                .fileName(originalFileName)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .sizeBytes(uploadSize)
                .cloudObjectKey(objectKey)
                .folderId(folderId)
                .deleted(false)
                .build();

        try {
            DriveFileResponse response = toResponse(fileRepository.saveAndFlush(file));
            log.info("event=drive_file_uploaded fileId={} sizeBytes={} storageProvider={}",
                    response.id(), response.sizeBytes(),
                    cloudStorageService.getClass().getSimpleName());
            return new UploadFileResponse("File uploaded successfully", response);
        } catch (RuntimeException exception) {
            compensateFailedMetadataSave(objectKey, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Page<DriveFileResponse> listFiles(
            AuthenticatedUser currentUser,
            Long folderId,
            int page,
            int size
    ) {
        String normalizedEmail = currentUser.email();
        authorizationService.requireEditableFolderOrRoot(currentUser, folderId);
        Pageable pageable = filePageable(page, size);
        Page<DriveFile> files = folderId == null
                ? fileRepository.findAllByUserEmailAndFolderIdIsNullAndDeletedFalse(
                        normalizedEmail, pageable)
                : fileRepository.findAllByUserEmailAndFolderIdAndDeletedFalse(
                        normalizedEmail, folderId, pageable);
        return files.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<DriveFileResponse> listRecentFiles(
            AuthenticatedUser currentUser,
            int page,
            int size
    ) {
        String normalizedEmail = currentUser.email();
        return fileRepository
                .findAllByUserEmailAndDeletedFalseAndLastOpenedAtIsNotNull(
                        normalizedEmail,
                        filePageable(page, size, "lastOpenedAt", Sort.Direction.DESC))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<DriveFileResponse> listStarredFiles(
            AuthenticatedUser currentUser,
            int page,
            int size
    ) {
        String normalizedEmail = currentUser.email();
        return fileRepository
                .findAllByUserEmailAndDeletedFalseAndStarredTrue(
                        normalizedEmail,
                        filePageable(page, size, "updatedAt", Sort.Direction.DESC))
                .map(this::toResponse);
    }

    @Transactional
    public DriveFileResponse renameFile(
            AuthenticatedUser currentUser,
            Long fileId,
            RenameFileRequest request
    ) {
        String normalizedEmail = currentUser.email();
        if (request == null) {
            throw new InvalidDriveRequestException("File rename request is required");
        }

        DriveFile file = authorizationService.requireEditableFile(currentUser, fileId);
        String fileName = sanitizeManagedFileName(request.fileName());
        validateBlockedExtension(fileName);
        file.setFileName(fileName);
        return toResponse(fileRepository.saveAndFlush(file));
    }

    @Transactional
    public DriveFileResponse moveFile(
            AuthenticatedUser currentUser,
            Long fileId,
            MoveFileRequest request
    ) {
        String normalizedEmail = currentUser.email();
        if (request == null) {
            throw new InvalidDriveRequestException("File move request is required");
        }

        DriveFile file = authorizationService.requireEditableFile(currentUser, fileId);
        authorizationService.requireEditableFolderOrRoot(currentUser, request.folderId());
        file.setFolderId(request.folderId());
        return toResponse(fileRepository.saveAndFlush(file));
    }

    @Transactional(readOnly = true)
    public Page<DriveFileResponse> searchFiles(
            AuthenticatedUser currentUser,
            String query,
            int page,
            int size
    ) {
        String normalizedEmail = currentUser.email();
        if (query == null || query.isBlank()) {
            throw new InvalidDriveRequestException("Search query cannot be blank");
        }

        return fileRepository
                .findAllByUserEmailAndDeletedFalseAndFileNameContainingIgnoreCase(
                        normalizedEmail, query.trim(), filePageable(page, size))
                .map(this::toResponse);
    }

    @Transactional
    public DriveFolderResponse createFolder(
            AuthenticatedUser currentUser,
            CreateFolderRequest request
    ) {
        String normalizedEmail = currentUser.email();
        if (request == null) {
            throw new InvalidDriveRequestException("Folder request is required");
        }
        String name = normalizeFolderName(request.name());
        authorizationService.requireEditableFolderOrRoot(currentUser, request.parentFolderId());

        DriveFolder folder = DriveFolder.builder()
                .userEmail(normalizedEmail)
                .name(name)
                .parentFolderId(request.parentFolderId())
                .deleted(false)
                .build();
        return toFolderResponse(folderRepository.save(folder));
    }

    @Transactional(readOnly = true)
    public List<DriveFolderResponse> listFolders(AuthenticatedUser currentUser, Long parentFolderId) {
        String normalizedEmail = currentUser.email();
        authorizationService.requireEditableFolderOrRoot(currentUser, parentFolderId);

        List<DriveFolder> folders = parentFolderId == null
                ? folderRepository
                .findAllByUserEmailAndParentFolderIdIsNullAndDeletedFalseOrderByNameAsc(
                        normalizedEmail)
                : folderRepository
                .findAllByUserEmailAndParentFolderIdAndDeletedFalseOrderByNameAsc(
                        normalizedEmail, parentFolderId);
        return folders.stream().map(this::toFolderResponse).toList();
    }

    @Transactional
    public DriveFolderResponse renameFolder(
            AuthenticatedUser currentUser,
            Long folderId,
            RenameFolderRequest request
    ) {
        String normalizedEmail = currentUser.email();
        if (request == null) {
            throw new InvalidDriveRequestException("Folder request is required");
        }
        DriveFolder folder = authorizationService.requireOwnedFolder(currentUser, folderId);
        folder.setName(normalizeFolderName(request.name()));
        return toFolderResponse(folderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(AuthenticatedUser currentUser, Long folderId) {
        String normalizedEmail = currentUser.email();
        DriveFolder folder = authorizationService.requireOwnedFolder(currentUser, folderId);

        softDeleteFolderTree(normalizedEmail, folder);
    }

    @Transactional(readOnly = true)
    public StorageUsageResponse getStorageUsage(AuthenticatedUser currentUser) {
        String normalizedEmail = currentUser.email();
        long usedBytes = fileRepository.sumStoredFileSizeByUserEmail(normalizedEmail);
        long storageLimitBytes = driveProperties.getStorageLimitBytes();
        long availableBytes = Math.max(0, storageLimitBytes - usedBytes);
        double usedPercentage = (usedBytes * 100.0) / storageLimitBytes;
        return new StorageUsageResponse(
                usedBytes,
                storageLimitBytes,
                availableBytes,
                usedPercentage);
    }

    @Transactional
    public DownloadedFile download(AuthenticatedUser currentUser, Long fileId) {
        DriveFile file = authorizationService.requireReadableFile(currentUser, fileId);
        markOpened(file);
        Resource resource = cloudStorageService.download(file.getCloudObjectKey());
        return new DownloadedFile(
                resource,
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSizeBytes());
    }

    @Transactional
    public PreviewFile preview(AuthenticatedUser currentUser, Long fileId) {
        DriveFile file = authorizationService.requireReadableFile(currentUser, fileId);
        markOpened(file);
        if (!isPreviewSupported(file.getContentType())) {
            return PreviewFile.unsupported(new FilePreviewMetadataResponse(
                    file.getId(),
                    file.getFileName(),
                    file.getOriginalFileName(),
                    file.getContentType(),
                    file.getSizeBytes(),
                    false,
                    "/api/drive/download/" + file.getId()));
        }

        Resource resource = cloudStorageService.download(file.getCloudObjectKey());
        return PreviewFile.supported(
                resource,
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSizeBytes());
    }

    @Transactional
    public void delete(AuthenticatedUser currentUser, Long fileId) {
        DriveFile file = authorizationService.requireOwnedFile(currentUser, fileId);
        file.setDeleted(true);
        fileRepository.saveAndFlush(file);
        log.info("event=drive_file_moved_to_trash fileId={}", file.getId());
    }

    @Transactional
    public DriveFileResponse starFile(AuthenticatedUser currentUser, Long fileId) {
        DriveFile file = authorizationService.requireOwnedFile(currentUser, fileId);
        file.setStarred(true);
        return toResponse(fileRepository.saveAndFlush(file));
    }

    @Transactional
    public DriveFileResponse unstarFile(AuthenticatedUser currentUser, Long fileId) {
        DriveFile file = authorizationService.requireOwnedFile(currentUser, fileId);
        file.setStarred(false);
        return toResponse(fileRepository.saveAndFlush(file));
    }

    @Transactional(readOnly = true)
    public DriveFileDetailsResponse getFileDetails(AuthenticatedUser currentUser, Long fileId) {
        DriveFile file = authorizationService.requireReadableFile(currentUser, fileId);
        boolean shared = shareLinkRepository.hasActiveLink(
                file.getId(),
                file.getUserEmail(),
                Instant.now());
        shared = shared || permissionRepository.existsByFileIdAndOwnerEmail(
                file.getId(),
                file.getUserEmail());
        List<DriveFolderResponse> folderPath = buildFolderPath(file.getUserEmail(), file.getFolderId());
        String location = folderPath.isEmpty()
                ? "My Drive"
                : "My Drive / " + folderPath.stream()
                .map(DriveFolderResponse::name)
                .reduce((left, right) -> left + " / " + right)
                .orElse("");
        return new DriveFileDetailsResponse(
                file.getId(),
                file.getFileName(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSizeBytes(),
                fileType(file),
                file.getUserEmail(),
                location,
                file.getFolderId(),
                folderPath,
                shared,
                file.isStarred(),
                file.getCreatedAt(),
                file.getUpdatedAt(),
                file.getLastOpenedAt());
    }

    @Transactional(readOnly = true)
    public DriveTrashResponse listTrash(AuthenticatedUser currentUser) {
        String normalizedEmail = currentUser.email();
        return new DriveTrashResponse(
                folderRepository.findAllByUserEmailAndDeletedTrueOrderByUpdatedAtDesc(
                                normalizedEmail)
                        .stream()
                        .map(this::toFolderResponse)
                        .toList(),
                fileRepository.findAllByUserEmailAndDeletedTrueOrderByUpdatedAtDesc(
                                normalizedEmail)
                        .stream()
                        .map(this::toResponse)
                        .toList());
    }

    @Transactional
    public DriveFileResponse restoreFile(AuthenticatedUser currentUser, Long fileId) {
        String normalizedEmail = currentUser.email();
        DriveFile file = authorizationService.requireOwnedTrashedFile(currentUser, fileId);
        if (file.getFolderId() != null
                && folderRepository.findByIdAndUserEmailAndDeletedFalse(
                        file.getFolderId(), normalizedEmail).isEmpty()) {
            file.setFolderId(null);
        }
        file.setDeleted(false);
        return toResponse(fileRepository.saveAndFlush(file));
    }

    @Transactional
    public void permanentlyDeleteFile(AuthenticatedUser currentUser, Long fileId) {
        DriveFile file = authorizationService.requireOwnedTrashedFile(currentUser, fileId);
        deleteStoredFile(file);
    }

    @Transactional
    public DriveFolderResponse restoreFolder(AuthenticatedUser currentUser, Long folderId) {
        String normalizedEmail = currentUser.email();
        DriveFolder folder = authorizationService.requireOwnedTrashedFolder(currentUser, folderId);
        if (folder.getParentFolderId() != null
                && folderRepository.findByIdAndUserEmailAndDeletedFalse(
                        folder.getParentFolderId(), normalizedEmail).isEmpty()) {
            folder.setParentFolderId(null);
        }
        restoreFolderTree(normalizedEmail, folder);
        return toFolderResponse(folder);
    }

    @Transactional
    public void permanentlyDeleteFolder(AuthenticatedUser currentUser, Long folderId) {
        String normalizedEmail = currentUser.email();
        DriveFolder folder = authorizationService.requireOwnedTrashedFolder(currentUser, folderId);
        permanentlyDeleteFolderTree(normalizedEmail, folder);
    }

    private void markOpened(DriveFile file) {
        file.setLastOpenedAt(Instant.now());
        fileRepository.saveAndFlush(file);
    }

    private List<DriveFolderResponse> buildFolderPath(String ownerEmail, Long folderId) {
        List<DriveFolderResponse> path = new ArrayList<>();
        Long currentFolderId = folderId;
        while (currentFolderId != null) {
            DriveFolder folder = folderRepository
                    .findByIdAndUserEmailAndDeletedFalse(currentFolderId, ownerEmail)
                    .orElse(null);
            if (folder == null) {
                break;
            }
            path.add(toFolderResponse(folder));
            currentFolderId = folder.getParentFolderId();
        }
        Collections.reverse(path);
        return path;
    }

    private String fileType(DriveFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "Unknown";
        }
        return contentType.split(";", 2)[0].trim();
    }

    private void softDeleteFolderTree(String ownerEmail, DriveFolder folder) {
        List<DriveFile> childFiles = fileRepository.findAllByUserEmailAndFolderId(
                ownerEmail, folder.getId());
        childFiles.forEach(file -> file.setDeleted(true));
        fileRepository.saveAll(childFiles);

        List<DriveFolder> childFolders = folderRepository.findAllByUserEmailAndParentFolderId(
                ownerEmail, folder.getId());
        childFolders.forEach(child -> softDeleteFolderTree(ownerEmail, child));

        folder.setDeleted(true);
        folderRepository.save(folder);
    }

    private void restoreFolderTree(String ownerEmail, DriveFolder folder) {
        folder.setDeleted(false);
        folderRepository.save(folder);

        List<DriveFolder> childFolders = folderRepository.findAllByUserEmailAndParentFolderId(
                ownerEmail, folder.getId());
        childFolders.stream()
                .filter(DriveFolder::isDeleted)
                .forEach(child -> restoreFolderTree(ownerEmail, child));

        List<DriveFile> childFiles = fileRepository.findAllByUserEmailAndFolderId(
                ownerEmail, folder.getId());
        childFiles.stream()
                .filter(DriveFile::isDeleted)
                .forEach(file -> file.setDeleted(false));
        fileRepository.saveAll(childFiles);
    }

    private void permanentlyDeleteFolderTree(String ownerEmail, DriveFolder folder) {
        List<DriveFolder> childFolders = new ArrayList<>(
                folderRepository.findAllByUserEmailAndParentFolderId(ownerEmail, folder.getId()));
        for (DriveFolder childFolder : childFolders) {
            permanentlyDeleteFolderTree(ownerEmail, childFolder);
        }

        List<DriveFile> childFiles = new ArrayList<>(
                fileRepository.findAllByUserEmailAndFolderId(ownerEmail, folder.getId()));
        for (DriveFile childFile : childFiles) {
            deleteStoredFile(childFile);
        }

        folderRepository.delete(folder);
    }

    private void deleteStoredFile(DriveFile file) {
        try {
            cloudStorageService.delete(file.getCloudObjectKey());
            shareLinkRepository.deleteAllByFileIdAndOwnerEmail(file.getId(), file.getUserEmail());
            permissionRepository.deleteAllByFileIdAndOwnerEmail(file.getId(), file.getUserEmail());
            fileRepository.delete(file);
            log.info("event=drive_file_permanently_deleted fileId={} storageProvider={}",
                    file.getId(), cloudStorageService.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            log.warn("event=drive_file_permanent_delete_failed fileId={} storageProvider={} reason={}",
                    file.getId(), cloudStorageService.getClass().getSimpleName(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private String normalizeFolderName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidDriveRequestException("Folder name cannot be blank");
        }
        return name.trim();
    }

    private void validateUpload(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new InvalidDriveRequestException("A non-empty file is required");
        }
        if (multipartFile.getSize() > driveProperties.getMaxUploadSizeBytes()) {
            throw new UploadSizeExceededException(
                    multipartFile.getSize(), driveProperties.getMaxUploadSizeBytes());
        }
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unnamed-file";
        }
        String normalized = Normalizer.normalize(originalFilename, Normalizer.Form.NFKC)
                .replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[<>:\"/\\\\|?*]", "_")
                .replaceAll("[. ]+$", "")
                .trim();
        if (baseName.isBlank()
                || baseName.equals(".")
                || baseName.equals("..")
                || WINDOWS_RESERVED_NAME.matcher(baseName).matches()) {
            return "unnamed-file";
        }
        return truncateFileName(baseName);
    }

    private String sanitizeManagedFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidDriveRequestException("File name cannot be blank");
        }
        String normalized = sanitizeFileName(fileName);
        if ("unnamed-file".equals(normalized)) {
            throw new InvalidDriveRequestException("File name is invalid");
        }
        return normalized;
    }

    private void validateBlockedExtension(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        Set<String> blockedExtensions = driveProperties.normalizedBlockedExtensions();
        if (blockedExtensions.stream().anyMatch(lowerCaseName::endsWith)) {
            throw new InvalidDriveRequestException("File extension is not allowed");
        }
    }

    private String preserveContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.trim();
    }

    private boolean isPreviewSupported(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return normalized.startsWith("image/")
                || normalized.startsWith("audio/")
                || normalized.startsWith("video/")
                || EXACT_PREVIEW_TYPES.contains(normalized);
    }

    private String truncateFileName(String fileName) {
        if (fileName.length() <= MAX_FILE_NAME_LENGTH) {
            return fileName;
        }
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart > 0 && fileName.length() - extensionStart <= 20) {
            String extension = fileName.substring(extensionStart);
            return fileName.substring(0, MAX_FILE_NAME_LENGTH - extension.length()) + extension;
        }
        return fileName.substring(0, MAX_FILE_NAME_LENGTH);
    }

    private Pageable filePageable(int page, int size) {
        return filePageable(page, size, "createdAt", Sort.Direction.DESC);
    }

    private Pageable filePageable(int page, int size, String sortProperty, Sort.Direction direction) {
        if (page < 0) {
            throw new InvalidDriveRequestException("Page index cannot be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidDriveRequestException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(direction, sortProperty));
    }

    private String buildStoredFileName(String originalFileName) {
        String filesystemSafeName = originalFileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return UUID.randomUUID() + "-" + filesystemSafeName;
    }

    private String buildObjectKey(String ownerEmail, String storedFileName) {
        return ownerEmail + "/" + storedFileName;
    }

    private void compensateFailedMetadataSave(String objectKey, RuntimeException originalException) {
        try {
            cloudStorageService.delete(objectKey);
            log.info("event=drive_upload_compensated storageProvider={}",
                    cloudStorageService.getClass().getSimpleName());
        } catch (RuntimeException cleanupException) {
            log.error(
                    "event=drive_upload_compensation_failed storageProvider={} reason={}",
                    cloudStorageService.getClass().getSimpleName(),
                    cleanupException.getClass().getSimpleName());
            originalException.addSuppressed(cleanupException);
        }
    }

    private DriveFileResponse toResponse(DriveFile file) {
        boolean shared = shareLinkRepository.hasActiveLink(
                file.getId(),
                file.getUserEmail(),
                Instant.now());
        shared = shared || permissionRepository.existsByFileIdAndOwnerEmail(
                file.getId(),
                file.getUserEmail());
        return new DriveFileResponse(
                file.getId(),
                file.getFileName(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getFolderId(),
                shared,
                file.isStarred(),
                file.getCreatedAt(),
                file.getUpdatedAt(),
                file.getLastOpenedAt());
    }

    private DriveFolderResponse toFolderResponse(DriveFolder folder) {
        return new DriveFolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentFolderId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt());
    }

    public record DownloadedFile(
            Resource resource,
            String originalFileName,
            String contentType,
            long sizeBytes
    ) {
    }

    public record PreviewFile(
            boolean previewSupported,
            Resource resource,
            FilePreviewMetadataResponse metadata,
            String originalFileName,
            String contentType,
            long sizeBytes
    ) {
        private static PreviewFile supported(
                Resource resource,
                String originalFileName,
                String contentType,
                long sizeBytes
        ) {
            return new PreviewFile(true, resource, null, originalFileName, contentType, sizeBytes);
        }

        private static PreviewFile unsupported(FilePreviewMetadataResponse metadata) {
            return new PreviewFile(false, null, metadata, null, null, 0);
        }
    }
}
