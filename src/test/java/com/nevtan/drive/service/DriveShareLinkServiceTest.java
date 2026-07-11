package com.nevtan.drive.service;

import com.nevtan.drive.config.DriveShareProperties;
import com.nevtan.drive.dto.CreateShareLinkRequest;
import com.nevtan.drive.dto.ShareLinkResponse;
import com.nevtan.drive.dto.UpdateShareLinkRequest;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DriveShareLink;
import com.nevtan.drive.exception.FileNotFoundException;
import com.nevtan.drive.exception.InvalidDriveRequestException;
import com.nevtan.drive.exception.ShareLinkNotFoundException;
import com.nevtan.drive.repository.DriveFileRepository;
import com.nevtan.drive.repository.DriveShareLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriveShareLinkServiceTest {

    private static final String OWNER_EMAIL = "owner@example.com";

    @Mock
    private DriveShareLinkRepository shareLinkRepository;

    @Mock
    private DriveFileRepository fileRepository;

    @Mock
    private CloudStorageService cloudStorageService;

    private DriveShareLinkService service;

    @BeforeEach
    void setUp() {
        DriveShareProperties properties = new DriveShareProperties();
        properties.setShareBaseUrl("https://app.example.com/drive/share");
        service = new DriveShareLinkService(
                shareLinkRepository,
                fileRepository,
                cloudStorageService,
                properties);
    }

    @Test
    void createsSecureShareLinkForOwnedFile() {
        DriveFile file = file();
        Instant expiry = Instant.now().plus(1, ChronoUnit.DAYS);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(file));
        when(shareLinkRepository.save(any(DriveShareLink.class))).thenAnswer(invocation -> {
            DriveShareLink link = invocation.getArgument(0);
            link.setId(10L);
            link.setCreatedAt(Instant.parse("2026-06-19T00:00:00Z"));
            link.setUpdatedAt(Instant.parse("2026-06-19T00:00:00Z"));
            return link;
        });

        ShareLinkResponse response = service.create(
                OWNER_EMAIL,
                1L,
                new CreateShareLinkRequest(expiry));

        ArgumentCaptor<DriveShareLink> linkCaptor =
                ArgumentCaptor.forClass(DriveShareLink.class);
        verify(shareLinkRepository).save(linkCaptor.capture());
        String token = linkCaptor.getValue().getToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(response.shareUrl())
                .isEqualTo("https://app.example.com/drive/share/" + token);
        assertThat(response.active()).isTrue();
        assertThat(response.expiresAt()).isEqualTo(expiry);
    }

    @Test
    void preventsAnotherUserFromCreatingLink() {
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(1L, OWNER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(OWNER_EMAIL, 1L, null))
                .isInstanceOf(FileNotFoundException.class);

        verify(shareLinkRepository, never()).save(any());
    }

    @Test
    void rejectsPastExpiry() {
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(file()));

        assertThatThrownBy(() -> service.create(
                OWNER_EMAIL,
                1L,
                new CreateShareLinkRequest(Instant.now().minusSeconds(1))))
                .isInstanceOf(InvalidDriveRequestException.class)
                .hasMessage("Share link expiry must be in the future");
    }

    @Test
    void downloadsActiveUnexpiredLinkWithoutUserEmail() {
        DriveShareLink link = shareLink(true, Instant.now().plusSeconds(60));
        DriveFile file = file();
        ByteArrayResource resource = new ByteArrayResource("shared".getBytes());
        when(shareLinkRepository.findByToken("valid-token")).thenReturn(Optional.of(link));
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(file));
        when(cloudStorageService.download(file.getCloudObjectKey())).thenReturn(resource);

        DriveShareLinkService.SharedDownload result = service.download("valid-token");

        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.originalFileName()).isEqualTo("document.pdf");
    }

    @Test
    void rejectsExpiredOrInactiveLinks() {
        when(shareLinkRepository.findByToken("expired"))
                .thenReturn(Optional.of(shareLink(true, Instant.now().minusSeconds(1))));
        when(shareLinkRepository.findByToken("inactive"))
                .thenReturn(Optional.of(shareLink(false, null)));

        assertThatThrownBy(() -> service.download("expired"))
                .isInstanceOf(ShareLinkNotFoundException.class);
        assertThatThrownBy(() -> service.download("inactive"))
                .isInstanceOf(ShareLinkNotFoundException.class);

        verify(cloudStorageService, never()).download(any());
    }

    @Test
    void listsOnlyActiveUnexpiredLinksForOwnedFile() {
        DriveShareLink active = shareLink(true, Instant.now().plusSeconds(60));
        active.setId(10L);
        DriveShareLink expired = shareLink(true, Instant.now().minusSeconds(1));
        expired.setId(11L);
        DriveShareLink inactive = shareLink(false, null);
        inactive.setId(12L);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(file()));
        when(shareLinkRepository.findAllByFileIdAndOwnerEmailOrderByCreatedAtDesc(
                1L, OWNER_EMAIL))
                .thenReturn(List.of(active, expired, inactive));

        List<ShareLinkResponse> links = service.listActiveForFile(OWNER_EMAIL, 1L);

        assertThat(links).extracting(ShareLinkResponse::id).containsExactly(10L);
    }

    @Test
    void preventsAnotherUserFromListingFileLinks() {
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(1L, OWNER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listActiveForFile(OWNER_EMAIL, 1L))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void onlyOwnerCanUpdateShareLink() {
        DriveShareLink link = shareLink(true, null);
        Instant expiry = Instant.now().plus(2, ChronoUnit.DAYS);
        when(shareLinkRepository.findByIdAndOwnerEmail(10L, OWNER_EMAIL))
                .thenReturn(Optional.of(link));
        when(shareLinkRepository.save(link)).thenReturn(link);

        ShareLinkResponse response = service.update(
                OWNER_EMAIL,
                10L,
                new UpdateShareLinkRequest(expiry, true));

        assertThat(link.getExpiresAt()).isEqualTo(expiry);
        assertThat(response.expiresAt()).isEqualTo(expiry);
        verify(shareLinkRepository).save(link);
    }

    @Test
    void rejectsPastExpiryDuringUpdate() {
        DriveShareLink link = shareLink(true, null);
        when(shareLinkRepository.findByIdAndOwnerEmail(10L, OWNER_EMAIL))
                .thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.update(
                OWNER_EMAIL,
                10L,
                new UpdateShareLinkRequest(Instant.now().minusSeconds(1), null)))
                .isInstanceOf(InvalidDriveRequestException.class)
                .hasMessage("Share link expiry must be in the future");
    }

    @Test
    void onlyOwnerCanDeactivateShareLink() {
        DriveShareLink link = shareLink(true, null);
        when(shareLinkRepository.findByIdAndOwnerEmail(10L, OWNER_EMAIL))
                .thenReturn(Optional.of(link));
        when(shareLinkRepository.save(link)).thenReturn(link);

        service.deactivate(OWNER_EMAIL, 10L);

        assertThat(link.isActive()).isFalse();
        verify(shareLinkRepository).save(link);
    }

    @Test
    void hidesShareLinkFromNonOwnerDuringDelete() {
        when(shareLinkRepository.findByIdAndOwnerEmail(10L, OWNER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(OWNER_EMAIL, 10L))
                .isInstanceOf(ShareLinkNotFoundException.class);
    }

    private DriveFile file() {
        return DriveFile.builder()
                .id(1L)
                .userEmail(OWNER_EMAIL)
                .fileName("document.pdf")
                .originalFileName("document.pdf")
                .contentType("application/pdf")
                .sizeBytes(100L)
                .cloudObjectKey(OWNER_EMAIL + "/stored-document.pdf")
                .starred(false)
                .deleted(false)
                .build();
    }

    private DriveShareLink shareLink(boolean active, Instant expiresAt) {
        return DriveShareLink.builder()
                .id(10L)
                .fileId(1L)
                .ownerEmail(OWNER_EMAIL)
                .token("valid-token")
                .expiresAt(expiresAt)
                .active(active)
                .build();
    }
}
