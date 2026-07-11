package com.nevtan.drive.repository;

import com.nevtan.drive.entity.DriveShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriveShareLinkRepository extends JpaRepository<DriveShareLink, Long> {

    Optional<DriveShareLink> findByToken(String token);

    Optional<DriveShareLink> findByIdAndOwnerEmail(Long id, String ownerEmail);

    List<DriveShareLink> findAllByFileIdAndOwnerEmailOrderByCreatedAtDesc(
            Long fileId,
            String ownerEmail);

    void deleteAllByFileIdAndOwnerEmail(Long fileId, String ownerEmail);

    boolean existsByToken(String token);

    @Query("""
            select count(link) > 0
            from DriveShareLink link
            where link.fileId = :fileId
              and link.ownerEmail = :ownerEmail
              and link.active = true
              and (link.expiresAt is null or link.expiresAt > :now)
            """)
    boolean hasActiveLink(
            @Param("fileId") Long fileId,
            @Param("ownerEmail") String ownerEmail,
            @Param("now") Instant now);
}
