package com.nevtan.drive.service;

import com.nevtan.drive.auth.AuthenticatedUser;
import com.nevtan.drive.config.DriveShareProperties;
import com.nevtan.drive.dto.CreateShareLinkRequest;
import com.nevtan.drive.dto.ShareLinkResponse;
import com.nevtan.drive.dto.UpdateShareLinkRequest;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DriveShareLink;
import com.nevtan.drive.exception.InvalidDriveRequestException;
import com.nevtan.drive.exception.ShareLinkNotFoundException;
import com.nevtan.drive.repository.DriveFileRepository;
import com.nevtan.drive.repository.DriveShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DriveShareLinkService {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final DriveShareLinkRepository shareLinkRepository;
    private final DriveFileRepository fileRepository;
    private final CloudStorageService cloudStorageService;
    private final DriveShareProperties shareProperties;
    private final DriveAuthorizationService authorizationService;

    @Transactional
    public ShareLinkResponse create(
            AuthenticatedUser currentUser,
            Long fileId,
            CreateShareLinkRequest request
    ) {
        String ownerEmail = currentUser.email();
        DriveFile file = authorizationService.requireOwnedFile(currentUser, fileId);

        Instant expiresAt = request == null ? null : request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new InvalidDriveRequestException("Share link expiry must be in the future");
        }

        DriveShareLink shareLink = DriveShareLink.builder()
                .fileId(file.getId())
                .ownerEmail(ownerEmail)
                .token(generateUniqueToken())
                .expiresAt(expiresAt)
                .active(true)
                .build();
        return toResponse(shareLinkRepository.save(shareLink));
    }

    @Transactional(readOnly = true)
    public List<ShareLinkResponse> listActiveForFile(AuthenticatedUser currentUser, Long fileId) {
        String ownerEmail = currentUser.email();
        authorizationService.requireOwnedFile(currentUser, fileId);
        Instant now = Instant.now();
        return shareLinkRepository
                .findAllByFileIdAndOwnerEmailOrderByCreatedAtDesc(fileId, ownerEmail)
                .stream()
                .filter(DriveShareLink::isActive)
                .filter(link -> !isExpired(link, now))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ShareLinkResponse update(AuthenticatedUser currentUser, Long shareId, UpdateShareLinkRequest request) {
        if (request == null) {
            throw new InvalidDriveRequestException("Share link update request is required");
        }
        String ownerEmail = currentUser.email();
        DriveShareLink shareLink = shareLinkRepository
                .findByIdAndOwnerEmail(shareId, ownerEmail)
                .orElseThrow(ShareLinkNotFoundException::new);

        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new InvalidDriveRequestException("Share link expiry must be in the future");
        }
        shareLink.setExpiresAt(request.expiresAt());
        if (request.active() != null) {
            shareLink.setActive(request.active());
        }
        return toResponse(shareLinkRepository.save(shareLink));
    }

    @Transactional(readOnly = true)
    public SharedDownload download(String token) {
        if (token == null || token.isBlank()) {
            throw new ShareLinkNotFoundException();
        }

        DriveShareLink shareLink = shareLinkRepository.findByToken(token)
                .filter(DriveShareLink::isActive)
                .filter(link -> !isExpired(link, Instant.now()))
                .orElseThrow(ShareLinkNotFoundException::new);

        DriveFile file = fileRepository
                .findByIdAndUserEmailAndDeletedFalse(
                        shareLink.getFileId(), shareLink.getOwnerEmail())
                .orElseThrow(ShareLinkNotFoundException::new);

        Resource resource = cloudStorageService.download(file.getCloudObjectKey());
        return new SharedDownload(
                resource,
                file.getOriginalFileName(),
                file.getContentType(),
                file.getSizeBytes());
    }

    @Transactional
    public void deactivate(AuthenticatedUser currentUser, Long shareId) {
        String ownerEmail = currentUser.email();
        DriveShareLink shareLink = shareLinkRepository
                .findByIdAndOwnerEmail(shareId, ownerEmail)
                .orElseThrow(ShareLinkNotFoundException::new);
        shareLink.setActive(false);
        shareLinkRepository.save(shareLink);
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
            byte[] randomBytes = new byte[TOKEN_BYTES];
            SECURE_RANDOM.nextBytes(randomBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            if (!shareLinkRepository.existsByToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Could not generate a unique share token");
    }

    private ShareLinkResponse toResponse(DriveShareLink shareLink) {
        String shareUrl = UriComponentsBuilder
                .fromUri(shareProperties.validatedBaseUrl())
                .pathSegment(shareLink.getToken())
                .build()
                .toUriString();
        return new ShareLinkResponse(
                shareLink.getId(),
                shareLink.getFileId(),
                shareUrl,
                shareLink.getExpiresAt(),
                shareLink.isActive(),
                shareLink.getCreatedAt(),
                shareLink.getUpdatedAt());
    }

    private boolean isExpired(DriveShareLink shareLink, Instant now) {
        return shareLink.getExpiresAt() != null && !shareLink.getExpiresAt().isAfter(now);
    }

    public record SharedDownload(
            Resource resource,
            String originalFileName,
            String contentType,
            long sizeBytes
    ) {
    }
}
