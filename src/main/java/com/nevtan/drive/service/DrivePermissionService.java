package com.nevtan.drive.service;

import com.nevtan.drive.dto.CreateDrivePermissionRequest;
import com.nevtan.drive.dto.DrivePermissionResponse;
import com.nevtan.drive.dto.SharedDriveFileResponse;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DrivePermission;
import com.nevtan.drive.entity.DrivePermissionRole;
import com.nevtan.drive.exception.DriveAccessDeniedException;
import com.nevtan.drive.exception.FileNotFoundException;
import com.nevtan.drive.exception.InvalidDriveRequestException;
import com.nevtan.drive.repository.DriveFileRepository;
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

    @Transactional
    public DrivePermissionResponse shareFile(
            String userEmail,
            Long fileId,
            CreateDrivePermissionRequest request
    ) {
        String ownerEmail = normalizeUserEmail(userEmail);
        if (request == null) {
            throw new InvalidDriveRequestException("Permission request is required");
        }
        DriveFile file = getOwnedActiveFile(ownerEmail, fileId);
        String sharedWithEmail = normalizeUserEmail(request.sharedWithEmail());
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

    @Transactional(readOnly = true)
    public List<DrivePermissionResponse> listForFile(String userEmail, Long fileId) {
        String ownerEmail = normalizeUserEmail(userEmail);
        getOwnedActiveFile(ownerEmail, fileId);
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

    @Transactional
    public void removeAccess(String userEmail, Long permissionId) {
        String ownerEmail = normalizeUserEmail(userEmail);
        DrivePermission permission = permissionRepository
                .findByIdAndOwnerEmail(permissionId, ownerEmail)
                .orElseThrow(DriveAccessDeniedException::new);
        permissionRepository.delete(permission);
    }

    @Transactional(readOnly = true)
    public List<SharedDriveFileResponse> listSharedWithMe(String userEmail) {
        String sharedWithEmail = normalizeUserEmail(userEmail);
        return permissionRepository.findAllBySharedWithEmailOrderByUpdatedAtDesc(sharedWithEmail)
                .stream()
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

    public String normalizeUserEmail(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new InvalidDriveRequestException("userEmail is required");
        }
        String normalized = userEmail.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_EMAIL.matcher(normalized).matches()
                || normalized.contains("/")
                || normalized.contains("\\")) {
            throw new InvalidDriveRequestException("userEmail is invalid");
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

    private DriveFile getOwnedActiveFile(String ownerEmail, Long fileId) {
        return fileRepository.findByIdAndUserEmailAndDeletedFalse(fileId, ownerEmail)
                .orElseThrow(() -> new FileNotFoundException(fileId));
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
