package com.nevtan.drive.repository;

import com.nevtan.drive.entity.DriveRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriveRefreshTokenRepository extends JpaRepository<DriveRefreshToken, Long> {

    Optional<DriveRefreshToken> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);
}
