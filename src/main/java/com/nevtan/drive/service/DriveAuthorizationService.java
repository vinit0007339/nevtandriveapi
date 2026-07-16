package com.nevtan.drive.service;

import com.nevtan.drive.auth.AuthenticatedUser;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DriveFolder;
import com.nevtan.drive.entity.DrivePermission;
import com.nevtan.drive.entity.DrivePermissionRole;
import com.nevtan.drive.exception.DriveAccessDeniedException;
import com.nevtan.drive.exception.FileNotFoundException;
import com.nevtan.drive.exception.FolderNotFoundException;
import com.nevtan.drive.repository.DriveFileRepository;
import com.nevtan.drive.repository.DriveFolderRepository;
import com.nevtan.drive.repository.DrivePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DriveAuthorizationService {

    private final DriveFileRepository fileRepository;
    private final DriveFolderRepository folderRepository;
    private final DrivePermissionRepository permissionRepository;

    public DriveFile requireReadableFile(AuthenticatedUser user, Long fileId) {
        DriveFile ownedFile = fileRepository.findByIdAndUserEmailAndDeletedFalse(fileId, user.email())
                .orElse(null);
        if (ownedFile != null) {
            return ownedFile;
        }
        DriveFile file = fileRepository.findById(fileId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new FileNotFoundException(fileId));
        DrivePermission permission = permissionRepository
                .findByFileIdAndSharedWithEmail(fileId, user.email())
                .filter(item -> item.getOwnerEmail().equals(file.getUserEmail()))
                .or(() -> findFolderPermission(user, file.getFolderId(), file.getUserEmail()))
                .orElseThrow(DriveAccessDeniedException::new);
        if (permission.getRole() == DrivePermissionRole.VIEWER
                || permission.getRole() == DrivePermissionRole.EDITOR
                || permission.getRole() == DrivePermissionRole.OWNER) {
            return file;
        }
        throw new DriveAccessDeniedException();
    }

    public DriveFile requireEditableFile(AuthenticatedUser user, Long fileId) {
        DriveFile file = requireReadableFile(user, fileId);
        if (file.getUserEmail().equals(user.email())) {
            return file;
        }
        DrivePermission permission = permissionRepository
                .findByFileIdAndSharedWithEmail(fileId, user.email())
                .filter(item -> item.getOwnerEmail().equals(file.getUserEmail()))
                .or(() -> findFolderPermission(user, file.getFolderId(), file.getUserEmail()))
                .orElseThrow(DriveAccessDeniedException::new);
        if (permission.getRole() == DrivePermissionRole.EDITOR
                || permission.getRole() == DrivePermissionRole.OWNER) {
            return file;
        }
        throw new DriveAccessDeniedException();
    }

    public DriveFile requireOwnedFile(AuthenticatedUser user, Long fileId) {
        return fileRepository.findByIdAndUserEmailAndDeletedFalse(fileId, user.email())
                .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    public DriveFile requireOwnedTrashedFile(AuthenticatedUser user, Long fileId) {
        return fileRepository.findByIdAndUserEmailAndDeletedTrue(fileId, user.email())
                .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    public DriveFolder requireReadableFolder(AuthenticatedUser user, Long folderId) {
        DriveFolder ownedFolder = folderRepository.findByIdAndUserEmailAndDeletedFalse(folderId, user.email())
                .orElse(null);
        if (ownedFolder != null) {
            return ownedFolder;
        }
        DriveFolder folder = folderRepository.findById(folderId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new FolderNotFoundException(folderId));
        DrivePermission permission = findFolderPermission(user, folder.getId(), folder.getUserEmail())
                .orElseThrow(DriveAccessDeniedException::new);
        if (permission.getRole() == DrivePermissionRole.VIEWER
                || permission.getRole() == DrivePermissionRole.EDITOR
                || permission.getRole() == DrivePermissionRole.OWNER) {
            return folder;
        }
        throw new DriveAccessDeniedException();
    }

    public DriveFolder requireEditableFolder(AuthenticatedUser user, Long folderId) {
        DriveFolder folder = requireReadableFolder(user, folderId);
        if (folder.getUserEmail().equals(user.email())) {
            return folder;
        }
        DrivePermission permission = findFolderPermission(user, folder.getId(), folder.getUserEmail())
                .orElseThrow(DriveAccessDeniedException::new);
        if (permission.getRole() == DrivePermissionRole.EDITOR
                || permission.getRole() == DrivePermissionRole.OWNER) {
            return folder;
        }
        throw new DriveAccessDeniedException();
    }

    public DriveFolder requireOwnedFolder(AuthenticatedUser user, Long folderId) {
        if (folderId == null) {
            throw new FolderNotFoundException(null);
        }
        return folderRepository.findByIdAndUserEmailAndDeletedFalse(folderId, user.email())
                .orElseThrow(() -> new FolderNotFoundException(folderId));
    }

    public DriveFolder requireOwnedTrashedFolder(AuthenticatedUser user, Long folderId) {
        if (folderId == null) {
            throw new FolderNotFoundException(null);
        }
        return folderRepository.findByIdAndUserEmailAndDeletedTrue(folderId, user.email())
                .orElseThrow(() -> new FolderNotFoundException(folderId));
    }

    public void requireEditableFolderOrRoot(AuthenticatedUser user, Long folderId) {
        if (folderId != null) {
            requireEditableFolder(user, folderId);
        }
    }

    private Optional<DrivePermission> findFolderPermission(
            AuthenticatedUser user,
            Long folderId,
            String ownerEmail
    ) {
        Long currentFolderId = folderId;
        while (currentFolderId != null) {
            DriveFolder folder = folderRepository.findById(currentFolderId)
                    .filter(item -> !item.isDeleted())
                    .orElse(null);
            if (folder == null || !folder.getUserEmail().equals(ownerEmail)) {
                return Optional.empty();
            }
            Optional<DrivePermission> permission = permissionRepository
                    .findByFolderIdAndSharedWithEmail(folder.getId(), user.email())
                    .filter(item -> item.getOwnerEmail().equals(ownerEmail));
            if (permission.isPresent()) {
                return permission;
            }
            currentFolderId = folder.getParentFolderId();
        }
        return Optional.empty();
    }
}
