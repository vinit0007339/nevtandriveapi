package com.nevtan.drive.repository;

import com.nevtan.drive.entity.DriveFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface DriveFolderRepository extends JpaRepository<DriveFolder, Long> {

    Optional<DriveFolder> findByIdAndUserEmailAndDeletedFalse(Long id, String userEmail);

    Optional<DriveFolder> findByIdAndUserEmailAndDeletedTrue(Long id, String userEmail);

    List<DriveFolder> findAllByUserEmailAndParentFolderIdIsNullAndDeletedFalseOrderByNameAsc(
            String userEmail);

    List<DriveFolder> findAllByUserEmailAndParentFolderIdAndDeletedFalseOrderByNameAsc(
            String userEmail, Long parentFolderId);

    boolean existsByUserEmailAndParentFolderIdAndDeletedFalse(
            String userEmail, Long parentFolderId);

    List<DriveFolder> findAllByUserEmailAndDeletedTrueOrderByUpdatedAtDesc(String userEmail);

    List<DriveFolder> findAllByUserEmailAndParentFolderId(String userEmail, Long parentFolderId);
}
