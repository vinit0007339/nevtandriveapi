package com.nevtan.drive.repository;

import com.nevtan.drive.entity.DriveUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriveUserRepository extends JpaRepository<DriveUser, Long> {

    Optional<DriveUser> findByEmail(String email);
}
