package com.nevtan.drive.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "drive_permissions", indexes = {
        @Index(name = "idx_drive_permission_file", columnList = "file_id"),
        @Index(name = "idx_drive_permission_shared_with", columnList = "shared_with_email")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_drive_permission_file_shared_with",
                columnNames = {"file_id", "shared_with_email"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrivePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "owner_email", nullable = false, length = 320)
    private String ownerEmail;

    @Column(name = "shared_with_email", nullable = false, length = 320)
    private String sharedWithEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DrivePermissionRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
