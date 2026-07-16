package com.nevtan.drive.repository;

import com.nevtan.drive.entity.DrivePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DrivePermissionRepository extends JpaRepository<DrivePermission, Long> {

    List<DrivePermission> findAllByFileIdAndOwnerEmailOrderByCreatedAtDesc(
            Long fileId,
            String ownerEmail);

    List<DrivePermission> findAllByFolderIdAndOwnerEmailOrderByCreatedAtDesc(
            Long folderId,
            String ownerEmail);

    List<DrivePermission> findAllBySharedWithEmailOrderByUpdatedAtDesc(String sharedWithEmail);

    Optional<DrivePermission> findByFileIdAndSharedWithEmail(Long fileId, String sharedWithEmail);

    Optional<DrivePermission> findByFolderIdAndSharedWithEmail(Long folderId, String sharedWithEmail);

    Optional<DrivePermission> findByIdAndOwnerEmail(Long id, String ownerEmail);

    void deleteAllByFileIdAndOwnerEmail(Long fileId, String ownerEmail);

    void deleteAllByFolderIdAndOwnerEmail(Long folderId, String ownerEmail);

    boolean existsByFileIdAndOwnerEmail(Long fileId, String ownerEmail);

    boolean existsByFolderIdAndOwnerEmail(Long folderId, String ownerEmail);
}
