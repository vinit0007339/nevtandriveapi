package com.nevtan.drive.repository;

import com.nevtan.drive.entity.DriveFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriveFileRepository extends JpaRepository<DriveFile, Long> {

    Page<DriveFile> findAllByUserEmailAndFolderIdIsNullAndDeletedFalse(
            String userEmail, Pageable pageable);

    Page<DriveFile> findAllByUserEmailAndFolderIdAndDeletedFalse(
            String userEmail, Long folderId, Pageable pageable);

    Optional<DriveFile> findByIdAndUserEmailAndDeletedFalse(Long id, String userEmail);

    Optional<DriveFile> findByIdAndUserEmailAndDeletedTrue(Long id, String userEmail);

    boolean existsByUserEmailAndFolderIdAndDeletedFalse(String userEmail, Long folderId);

    List<DriveFile> findAllByUserEmailAndDeletedTrueOrderByUpdatedAtDesc(String userEmail);

    List<DriveFile> findAllByUserEmailAndFolderId(String userEmail, Long folderId);

    Page<DriveFile> findAllByUserEmailAndDeletedFalseAndFileNameContainingIgnoreCase(
            String userEmail, String fileName, Pageable pageable);

    Page<DriveFile> findAllByUserEmailAndDeletedFalseAndLastOpenedAtIsNotNull(
            String userEmail, Pageable pageable);

    Page<DriveFile> findAllByUserEmailAndDeletedFalseAndStarredTrue(
            String userEmail, Pageable pageable);

    @Query("""
            select coalesce(sum(file.sizeBytes), 0)
            from DriveFile file
            where file.userEmail = :userEmail
            """)
    long sumStoredFileSizeByUserEmail(@Param("userEmail") String userEmail);
}
