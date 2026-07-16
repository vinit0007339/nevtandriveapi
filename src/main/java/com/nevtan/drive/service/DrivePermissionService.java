package com.nevtan.drive.service;

import com.nevtan.drive.auth.AuthenticatedUser;
import com.nevtan.drive.dto.CreateDrivePermissionRequest;
import com.nevtan.drive.dto.DriveSharedWithMeResponse;
import com.nevtan.drive.dto.DrivePermissionResponse;
import com.nevtan.drive.dto.SharedDriveFileResponse;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DriveFolder;
import com.nevtan.drive.entity.DrivePermission;
import com.nevtan.drive.entity.DrivePermissionRole;
import com.nevtan.drive.exception.DriveAccessDeniedException;
import com.nevtan.drive.exception.InvalidDriveRequestException;
import com.nevtan.drive.repository.DriveFileRepository;
import com.nevtan.drive.repository.DriveFolderRepository;
import com.nevtan.drive.repository.DrivePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DrivePermissionService {

    private static final Pattern SAFE_EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$");

    private final DrivePermissionRepository permissionRepository;
    private final DriveFileRepository fileRepository;
    private final DriveFolderRepository folderRepository;
    private final DriveAuthorizationService authorizationService;

    @Transactional
    public DrivePermissionResponse shareFile(
            AuthenticatedUser currentUser,
            Long fileId,
            CreateDrivePermissionRequest request
    ) {
        String ownerEmail = currentUser.email();
        if (request == null) {
            throw new InvalidDriveRequestException("Permission request is required");
        }
        DriveFile file = authorizationService.requireOwnedFile(currentUser, fileId);
        String sharedWithEmail = normalizeRecipientEmail(request.sharedWithEmail());
        if (ownerEmail.equals(sharedWithEmail)) {
            throw new InvalidDriveRequestException("Use the owner role for the file owner");
        }
        DrivePermissionRole role = parseRole(request.role());
        if (role == DrivePermissionRole.OWNER) {
            throw new InvalidDriveRequestException("Owner permissions are reserved for file owners");
        }

        DrivePermission permission = permissionRepository
                .findByFileIdAndSharedWithEmail(file.getId(), sharedWithEmail)
                .orElseGet(() -> DrivePermission.builder()
                        .fileId(file.getId())
                        .ownerEmail(ownerEmail)
                        .sharedWithEmail(sharedWithEmail)
                        .build());
        if (!ownerEmail.equals(permission.getOwnerEmail())) {
            throw new DriveAccessDeniedException();
        }
        permission.setRole(role);
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public DrivePermissionResponse shareFolder(
            AuthenticatedUser currentUser,
            Long folderId,
            CreateDrivePermissionRequest request
    ) {
        String ownerEmail = currentUser.email();
        if (request == null) {
            throw new InvalidDriveRequestException("Permission request is required");
        }
        DriveFolder folder = authorizationService.requireOwnedFolder(currentUser, folderId);
        String sharedWithEmail = normalizeRecipientEmail(request.sharedWithEmail());
        if (ownerEmail.equals(sharedWithEmail)) {
            throw new InvalidDriveRequestException("Use the owner role for the folder owner");
        }
        DrivePermissionRole role = parseRole(request.role());
        if (role == DrivePermissionRole.OWNER) {
            throw new InvalidDriveRequestException("Owner permissions are reserved for folder owners");
        }

        DrivePermission permission = permissionRepository
                .findByFolderIdAndSharedWithEmail(folder.getId(), sharedWithEmail)
                .orElseGet(() -> DrivePermission.builder()
                        .folderId(folder.getId())
                        .ownerEmail(ownerEmail)
                        .sharedWithEmail(sharedWithEmail)
                        .build());
        if (!ownerEmail.equals(permission.getOwnerEmail())) {
            throw new DriveAccessDeniedException();
        }
        permission.setRole(role);
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional(readOnly = true)
    public List<DrivePermissionResponse> listForFile(AuthenticatedUser currentUser, Long fileId) {
        String ownerEmail = currentUser.email();
        authorizationService.requireOwnedFile(currentUser, fileId);
        List<DrivePermissionResponse> sharedPermissions = permissionRepository
                .findAllByFileIdAndOwnerEmailOrderByCreatedAtDesc(fileId, ownerEmail)
                .stream()
                .map(this::toResponse)
                .toList();
        return java.util.stream.Stream
                .concat(
                        java.util.stream.Stream.of(ownerPermission(fileId, ownerEmail)),
                        sharedPermissions.stream())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DrivePermissionResponse> listForFolder(AuthenticatedUser currentUser, Long folderId) {
        String ownerEmail = currentUser.email();
        authorizationService.requireOwnedFolder(currentUser, folderId);
        List<DrivePermissionResponse> sharedPermissions = permissionRepository
                .findAllByFolderIdAndOwnerEmailOrderByCreatedAtDesc(folderId, ownerEmail)
                .stream()
                .map(this::toResponse)
                .toList();
        return java.util.stream.Stream
                .concat(
                        java.util.stream.Stream.of(ownerPermission(null, ownerEmail)),
                        sharedPermissions.stream())
                .toList();
    }

    @Transactional
    public void removeAccess(AuthenticatedUser currentUser, Long permissionId) {
        String ownerEmail = currentUser.email();
        DrivePermission permission = permissionRepository
                .findByIdAndOwnerEmail(permissionId, ownerEmail)
                .orElseThrow(DriveAccessDeniedException::new);
        permissionRepository.delete(permission);
    }

    @Transactional(readOnly = true)
    public DriveSharedWithMeResponse listSharedWithMe(AuthenticatedUser currentUser) {
        String sharedWithEmail = currentUser.email();
        List<SharedDriveFileResponse> files = permissionRepository
                .findAllBySharedWithEmailOrderByUpdatedAtDesc(sharedWithEmail)
                .stream()
                .filter(permission -> permission.getFileId() != null)
                .map(permission -> fileRepository
                        .findByIdAndUserEmailAndDeletedFalse(
                                permission.getFileId(),
                                permission.getOwnerEmail())
                        .map(file -> toSharedFileResponse(file, permission))
                        .orElse(null))
                .filter(response -> response != null)
                .sorted(Comparator.comparing(
                        SharedDriveFileResponse::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<com.nevtan.drive.dto.SharedDriveFolderResponse> folders = permissionRepository
                .findAllBySharedWithEmailOrderByUpdatedAtDesc(sharedWithEmail)
                .stream()
                .filter(permission -> permission.getFolderId() != null)
                .map(permission -> folderRepository
                        .findByIdAndUserEmailAndDeletedFalse(
                                permission.getFolderId(),
                                permission.getOwnerEmail())
                        .map(folder -> toSharedFolderResponse(folder, permission))
                        .orElse(null))
                .filter(response -> response != null)
                .sorted(Comparator.comparing(
                        com.nevtan.drive.dto.SharedDriveFolderResponse::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new DriveSharedWithMeResponse(files, folders);
    }

    public DrivePermissionRole parseRole(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDriveRequestException("Permission role is required");
        }
        try {
            return DrivePermissionRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidDriveRequestException("Permission role must be viewer, editor, or owner");
        }
    }

    public String normalizeRecipientEmail(String emailValue) {
        if (emailValue == null || emailValue.isBlank()) {
            throw new InvalidDriveRequestException("Email is required");
        }
        String normalized = emailValue.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_EMAIL.matcher(normalized).matches()
                || normalized.contains("/")
                || normalized.contains("\\")) {
            throw new InvalidDriveRequestException("Email is invalid");
        }
        return normalized;
    }

    public DrivePermissionResponse ownerPermission(Long fileId, String ownerEmail) {
        Instant now = Instant.now();
        return new DrivePermissionResponse(
                null,
                fileId,
                ownerEmail,
                ownerEmail,
                DrivePermissionRole.OWNER.name().toLowerCase(Locale.ROOT),
                now,
                now);
    }

    private DrivePermissionResponse toResponse(DrivePermission permission) {
        return new DrivePermissionResponse(
                permission.getId(),
                permission.getFileId(),
                permission.getOwnerEmail(),
                permission.getSharedWithEmail(),
                permission.getRole().name().toLowerCase(Locale.ROOT),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }

    private com.nevtan.drive.dto.SharedDriveFolderResponse toSharedFolderResponse(
            DriveFolder folder,
            DrivePermission permission
    ) {
        return new com.nevtan.drive.dto.SharedDriveFolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentFolderId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt(),
                permission.getId(),
                permission.getOwnerEmail(),
                permission.getSharedWithEmail(),
                permission.getRole().name().toLowerCase(Locale.ROOT));
    }

    private SharedDriveFileResponse toSharedFileResponse(
            DriveFile file,
            DrivePermission permission
    ) {
        return new SharedDriveFileResponse(
                file.getId(),
                file.getFileName(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getFolderId(),
                true,
                file.isStarred(),
                file.getCreatedAt(),
                file.getUpdatedAt(),
                file.getLastOpenedAt(),
                permission.getId(),
                permission.getOwnerEmail(),
                permission.getSharedWithEmail(),
                permission.getRole().name().toLowerCase(Locale.ROOT));
    }
}
