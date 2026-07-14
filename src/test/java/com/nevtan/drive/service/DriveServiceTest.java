package com.nevtan.drive.service;

import com.nevtan.drive.auth.AuthenticatedUser;
import com.nevtan.drive.config.DriveProperties;
import com.nevtan.drive.dto.CreateFolderRequest;
import com.nevtan.drive.dto.DriveFileResponse;
import com.nevtan.drive.dto.DriveFolderResponse;
import com.nevtan.drive.dto.MoveFileRequest;
import com.nevtan.drive.dto.RenameFileRequest;
import com.nevtan.drive.dto.RenameFolderRequest;
import com.nevtan.drive.dto.StorageUsageResponse;
import com.nevtan.drive.dto.UploadFileResponse;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DriveFolder;
import com.nevtan.drive.exception.FolderNotFoundException;
import com.nevtan.drive.exception.FileNotFoundException;
import com.nevtan.drive.exception.InvalidDriveRequestException;
import com.nevtan.drive.exception.StorageLimitExceededException;
import com.nevtan.drive.exception.UploadSizeExceededException;
import com.nevtan.drive.repository.DriveFileRepository;
import com.nevtan.drive.repository.DriveFolderRepository;
import com.nevtan.drive.repository.DrivePermissionRepository;
import com.nevtan.drive.repository.DriveShareLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriveServiceTest {

    private static final String USER_EMAIL = "user@example.com";
    private static final AuthenticatedUser USER = new AuthenticatedUser(USER_EMAIL, USER_EMAIL);

    @Mock
    private DriveFileRepository fileRepository;

    @Mock
    private DriveFolderRepository folderRepository;

    @Mock
    private DrivePermissionRepository permissionRepository;

    @Mock
    private DriveShareLinkRepository shareLinkRepository;

    @Mock
    private CloudStorageService cloudStorageService;

    private DriveService driveService;

    @BeforeEach
    void setUp() {
        driveService = createService(new DriveProperties());
    }

    @Test
    void uploadsToCloudBeforeSavingMetadata() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "NevTan".getBytes());
        when(fileRepository.sumStoredFileSizeByUserEmail(USER_EMAIL)).thenReturn(100L);
        when(fileRepository.saveAndFlush(any(DriveFile.class))).thenAnswer(invocation -> {
            DriveFile file = invocation.getArgument(0);
            file.setId(42L);
            return file;
        });

        UploadFileResponse result = driveService.upload(USER, null, multipartFile);

        ArgumentCaptor<DriveFile> metadataCaptor = ArgumentCaptor.forClass(DriveFile.class);
        verify(cloudStorageService).upload(
                anyString(),
                any(),
                org.mockito.ArgumentMatchers.eq(6L),
                org.mockito.ArgumentMatchers.eq("text/plain"));
        verify(fileRepository).saveAndFlush(metadataCaptor.capture());

        DriveFile metadata = metadataCaptor.getValue();
        assertThat(metadata.getCloudObjectKey()).startsWith(USER_EMAIL + "/");
        assertThat(metadata.getFileName()).isEqualTo("notes.txt");
        assertThat(metadata.getOriginalFileName()).isEqualTo("notes.txt");
        assertThat(metadata.getSizeBytes()).isEqualTo(6L);
        assertThat(metadata.isDeleted()).isFalse();
        assertThat(result.file().id()).isEqualTo(42L);
    }

    @Test
    void rejectsUploadThatWouldExceedOneGigabyte() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "large.bin",
                "application/octet-stream",
                new byte[10]);
        when(fileRepository.sumStoredFileSizeByUserEmail(USER_EMAIL))
                .thenReturn(new DriveProperties().getStorageLimitBytes() - 5);

        assertThatThrownBy(() -> driveService.upload(USER, null, multipartFile))
                .isInstanceOf(StorageLimitExceededException.class);

        verify(cloudStorageService, never()).upload(
                anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(fileRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUploadAboveConfiguredMaximum() throws Exception {
        DriveProperties properties = new DriveProperties();
        properties.setMaxUploadSizeBytes(5L);
        driveService = createService(properties);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "large.txt", "text/plain", "123456".getBytes());

        assertThatThrownBy(() -> driveService.upload(USER, null, multipartFile))
                .isInstanceOf(UploadSizeExceededException.class)
                .hasMessageContaining("exceeds maximum");

        verify(cloudStorageService, never()).upload(
                anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void rejectsConfiguredBlockedExtensionCaseInsensitively() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "installer.EXE", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> driveService.upload(USER, null, multipartFile))
                .isInstanceOf(InvalidDriveRequestException.class)
                .hasMessage("File extension is not allowed");
    }

    @Test
    void sanitizesFilenameAndPreservesMimeType() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "../quarterly:report?.pdf",
                "application/pdf",
                "pdf".getBytes());
        when(fileRepository.sumStoredFileSizeByUserEmail(USER_EMAIL)).thenReturn(0L);
        when(fileRepository.saveAndFlush(any(DriveFile.class))).thenAnswer(invocation -> {
            DriveFile saved = invocation.getArgument(0);
            saved.setId(70L);
            return saved;
        });

        UploadFileResponse result = driveService.upload(USER, null, multipartFile);

        assertThat(result.file().fileName()).isEqualTo("quarterly_report_.pdf");
        assertThat(result.file().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void fallsBackToBinaryMimeTypeWhenUploadMimeTypeIsMissing() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "data.bin", null, "data".getBytes());
        when(fileRepository.sumStoredFileSizeByUserEmail(USER_EMAIL)).thenReturn(0L);
        when(fileRepository.saveAndFlush(any(DriveFile.class))).thenAnswer(invocation -> {
            DriveFile saved = invocation.getArgument(0);
            saved.setId(71L);
            return saved;
        });

        UploadFileResponse result = driveService.upload(USER, null, multipartFile);

        assertThat(result.file().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void removesUploadedObjectWhenMetadataFlushFails() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "data".getBytes());
        when(fileRepository.sumStoredFileSizeByUserEmail(USER_EMAIL)).thenReturn(0L);
        when(fileRepository.saveAndFlush(any(DriveFile.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> driveService.upload(USER, null, multipartFile))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(cloudStorageService).delete(objectKey.capture());
        assertThat(objectKey.getValue()).startsWith(USER_EMAIL + "/");
    }

    @Test
    void uploadsFileIntoOwnedFolder() {
        DriveFolder folder = folder(5L, "Documents", null);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "content".getBytes());
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(5L, USER_EMAIL))
                .thenReturn(Optional.of(folder));
        when(fileRepository.sumStoredFileSizeByUserEmail(USER_EMAIL)).thenReturn(0L);
        when(fileRepository.saveAndFlush(any(DriveFile.class))).thenAnswer(invocation -> {
            DriveFile saved = invocation.getArgument(0);
            saved.setId(6L);
            return saved;
        });

        UploadFileResponse result = driveService.upload(USER, 5L, multipartFile);

        assertThat(result.file().folderId()).isEqualTo(5L);
    }

    @Test
    void calculatesStorageUsage() {
        when(fileRepository.sumStoredFileSizeByUserEmail(USER_EMAIL))
                .thenReturn(new DriveProperties().getStorageLimitBytes() / 4);

        StorageUsageResponse usage = driveService.getStorageUsage(USER);

        long storageLimit = new DriveProperties().getStorageLimitBytes();
        assertThat(usage.usedBytes()).isEqualTo(storageLimit / 4);
        assertThat(usage.availableBytes()).isEqualTo(storageLimit * 3 / 4);
        assertThat(usage.usedPercentage()).isEqualTo(25.0);
    }

    @Test
    void listsOnlyRepositoryDtoData() {
        DriveFile file = file(1L);
        when(fileRepository
                .findAllByUserEmailAndFolderIdIsNullAndDeletedFalse(
                        org.mockito.ArgumentMatchers.eq(USER_EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<DriveFileResponse> result = driveService.listFiles(USER, null, 0, 20);

        assertThat(result.getContent()).containsExactly(new DriveFileResponse(
                1L,
                "document.pdf",
                "document.pdf",
                "application/pdf",
                123L,
                null,
                false,
                false,
                file.getCreatedAt(),
                file.getUpdatedAt(),
                file.getLastOpenedAt()));
    }

    @Test
    void listsFilesOnlyFromOwnedFolder() {
        DriveFolder folder = folder(8L, "Documents", null);
        DriveFile file = file(1L);
        file.setFolderId(8L);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(8L, USER_EMAIL))
                .thenReturn(Optional.of(folder));
        when(fileRepository
                .findAllByUserEmailAndFolderIdAndDeletedFalse(
                        org.mockito.ArgumentMatchers.eq(USER_EMAIL),
                        org.mockito.ArgumentMatchers.eq(8L),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<DriveFileResponse> result = driveService.listFiles(USER, 8L, 0, 20);

        assertThat(result.getContent()).extracting(DriveFileResponse::folderId)
                .containsExactly(8L);
    }

    @Test
    void listsRecentFilesByLastOpenedAt() {
        DriveFile file = file(91L);
        file.setLastOpenedAt(Instant.parse("2026-06-20T00:00:00Z"));
        when(fileRepository.findAllByUserEmailAndDeletedFalseAndLastOpenedAtIsNotNull(
                org.mockito.ArgumentMatchers.eq(USER_EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<DriveFileResponse> result = driveService.listRecentFiles(USER, 0, 20);

        assertThat(result.getContent()).extracting(DriveFileResponse::id).containsExactly(91L);
        assertThat(result.getContent().get(0).lastOpenedAt()).isEqualTo(file.getLastOpenedAt());
    }

    @Test
    void listsStarredFiles() {
        DriveFile file = file(92L);
        file.setStarred(true);
        when(fileRepository.findAllByUserEmailAndDeletedFalseAndStarredTrue(
                org.mockito.ArgumentMatchers.eq(USER_EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<DriveFileResponse> result = driveService.listStarredFiles(USER, 0, 20);

        assertThat(result.getContent()).extracting(DriveFileResponse::starred).containsExactly(true);
    }

    @Test
    void starsAndUnstarsFile() {
        DriveFile file = file(93L);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(93L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(fileRepository.saveAndFlush(file)).thenReturn(file);

        assertThat(driveService.starFile(USER, 93L).starred()).isTrue();
        assertThat(file.isStarred()).isTrue();

        assertThat(driveService.unstarFile(USER, 93L).starred()).isFalse();
        assertThat(file.isStarred()).isFalse();
    }

    @Test
    void returnsFileDetailsWithLocation() {
        DriveFolder folder = folder(94L, "Projects", null);
        DriveFile file = file(95L);
        file.setFolderId(94L);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(95L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(94L, USER_EMAIL))
                .thenReturn(Optional.of(folder));

        var result = driveService.getFileDetails(USER, 95L);

        assertThat(result.owner()).isEqualTo(USER_EMAIL);
        assertThat(result.location()).isEqualTo("My Drive / Projects");
        assertThat(result.type()).isEqualTo("application/pdf");
    }

    @Test
    void renamesOwnedFileWithoutExposingOrChangingCloudObjectKey() {
        DriveFile file = file(12L);
        String objectKey = file.getCloudObjectKey();
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(12L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(fileRepository.saveAndFlush(file)).thenReturn(file);

        DriveFileResponse result = driveService.renameFile(
                USER,
                12L,
                new RenameFileRequest(" quarterly report.pdf "));

        assertThat(result.fileName()).isEqualTo("quarterly report.pdf");
        assertThat(result.originalFileName()).isEqualTo("document.pdf");
        assertThat(file.getCloudObjectKey()).isEqualTo(objectKey);
    }

    @Test
    void rejectsBlankFileName() {
        DriveFile file = file(13L);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(13L, USER_EMAIL))
                .thenReturn(Optional.of(file));

        assertThatThrownBy(() -> driveService.renameFile(
                USER,
                13L,
                new RenameFileRequest("   ")))
                .isInstanceOf(InvalidDriveRequestException.class)
                .hasMessage("File name cannot be blank");
    }

    @Test
    void cannotRenameAnotherUsersFile() {
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(14L, USER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> driveService.renameFile(
                USER,
                14L,
                new RenameFileRequest("new-name.txt")))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void movesOwnedFileToOwnedFolder() {
        DriveFile file = file(15L);
        DriveFolder destination = folder(16L, "Destination", null);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(15L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(16L, USER_EMAIL))
                .thenReturn(Optional.of(destination));
        when(fileRepository.saveAndFlush(file)).thenReturn(file);

        DriveFileResponse result = driveService.moveFile(
                USER,
                15L,
                new MoveFileRequest(16L));

        assertThat(result.folderId()).isEqualTo(16L);
    }

    @Test
    void movesOwnedFileToRoot() {
        DriveFile file = file(17L);
        file.setFolderId(16L);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(17L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(fileRepository.saveAndFlush(file)).thenReturn(file);

        DriveFileResponse result = driveService.moveFile(
                USER,
                17L,
                new MoveFileRequest(null));

        assertThat(result.folderId()).isNull();
    }

    @Test
    void rejectsMoveToAnotherUsersFolder() {
        DriveFile file = file(18L);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(18L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(19L, USER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> driveService.moveFile(
                USER,
                18L,
                new MoveFileRequest(19L)))
                .isInstanceOf(FolderNotFoundException.class);

        verify(fileRepository, never()).saveAndFlush(file);
    }

    @Test
    void searchesOwnedActiveFilesCaseInsensitively() {
        DriveFile file = file(20L);
        file.setFileName("Quarterly Report.PDF");
        when(fileRepository
                .findAllByUserEmailAndDeletedFalseAndFileNameContainingIgnoreCase(
                        org.mockito.ArgumentMatchers.eq(USER_EMAIL),
                        org.mockito.ArgumentMatchers.eq("report"),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<DriveFileResponse> result =
                driveService.searchFiles(USER, " report ", 0, 20);

        assertThat(result.getContent()).extracting(DriveFileResponse::fileName)
                .containsExactly("Quarterly Report.PDF");
    }

    @Test
    void rejectsBlankSearchQuery() {
        assertThatThrownBy(() -> driveService.searchFiles(USER, " ", 0, 20))
                .isInstanceOf(InvalidDriveRequestException.class)
                .hasMessage("Search query cannot be blank");
    }

    @Test
    void rejectsUnsafePaginationValues() {
        assertThatThrownBy(() -> driveService.listFiles(USER, null, -1, 20))
                .isInstanceOf(InvalidDriveRequestException.class);
        assertThatThrownBy(() -> driveService.searchFiles(USER, "report", 0, 101))
                .isInstanceOf(InvalidDriveRequestException.class);
    }

    @Test
    void downloadsContentForOwnedFile() {
        DriveFile file = file(7L);
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource("data".getBytes());
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(7L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(cloudStorageService.download(file.getCloudObjectKey())).thenReturn(resource);

        DriveService.DownloadedFile result = driveService.download(USER, 7L);

        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.originalFileName()).isEqualTo("document.pdf");
        assertThat(file.getLastOpenedAt()).isNotNull();
        verify(fileRepository).saveAndFlush(file);
    }

    @Test
    void previewsSupportedOwnedFileInline() {
        DriveFile file = file(72L);
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource("pdf".getBytes());
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(72L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(cloudStorageService.download(file.getCloudObjectKey())).thenReturn(resource);

        DriveService.PreviewFile result = driveService.preview(USER, 72L);

        assertThat(result.previewSupported()).isTrue();
        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.metadata()).isNull();
        assertThat(file.getLastOpenedAt()).isNotNull();
        verify(fileRepository).saveAndFlush(file);
    }

    @Test
    void previewReturnsMetadataForUnsupportedOwnedFile() {
        DriveFile file = file(73L);
        file.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        file.setFileName("proposal.docx");
        file.setOriginalFileName("proposal.docx");
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(73L, USER_EMAIL))
                .thenReturn(Optional.of(file));

        DriveService.PreviewFile result = driveService.preview(USER, 73L);

        assertThat(result.previewSupported()).isFalse();
        assertThat(result.metadata().fileName()).isEqualTo("proposal.docx");
        assertThat(result.metadata().downloadUrl()).isEqualTo("/api/drive/download/73");
        assertThat(file.getLastOpenedAt()).isNotNull();
        verify(cloudStorageService, never()).download(anyString());
        verify(fileRepository).saveAndFlush(file);
    }

    @Test
    void deleteMovesFileToTrashWithoutDeletingCloudObject() {
        DriveFile file = file(9L);
        when(fileRepository.findByIdAndUserEmailAndDeletedFalse(9L, USER_EMAIL))
                .thenReturn(Optional.of(file));

        driveService.delete(USER, 9L);

        verify(cloudStorageService, never()).delete(file.getCloudObjectKey());
        assertThat(file.isDeleted()).isTrue();
        verify(fileRepository).saveAndFlush(file);
    }

    @Test
    void restoresTrashedFileToRootWhenOriginalFolderIsStillTrashed() {
        DriveFile file = file(10L);
        file.setDeleted(true);
        file.setFolderId(99L);
        when(fileRepository.findByIdAndUserEmailAndDeletedTrue(10L, USER_EMAIL))
                .thenReturn(Optional.of(file));
        when(fileRepository.saveAndFlush(file)).thenReturn(file);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(99L, USER_EMAIL))
                .thenReturn(Optional.empty());

        DriveFileResponse result = driveService.restoreFile(USER, 10L);

        assertThat(result.folderId()).isNull();
        assertThat(file.isDeleted()).isFalse();
    }

    @Test
    void createsFolderUnderOwnedParent() {
        DriveFolder parent = folder(10L, "Parent", null);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(10L, USER_EMAIL))
                .thenReturn(Optional.of(parent));
        when(folderRepository.save(any(DriveFolder.class))).thenAnswer(invocation -> {
            DriveFolder saved = invocation.getArgument(0);
            saved.setId(11L);
            saved.setCreatedAt(Instant.parse("2026-06-19T00:00:00Z"));
            saved.setUpdatedAt(Instant.parse("2026-06-19T00:00:00Z"));
            return saved;
        });

        DriveFolderResponse result = driveService.createFolder(
                USER,
                new CreateFolderRequest(" Projects ", 10L));

        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.name()).isEqualTo("Projects");
        assertThat(result.parentFolderId()).isEqualTo(10L);
    }

    @Test
    void rejectsFolderCreationUnderAnotherUsersParent() {
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(10L, USER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> driveService.createFolder(
                USER,
                new CreateFolderRequest("Projects", 10L)))
                .isInstanceOf(FolderNotFoundException.class);

        verify(folderRepository, never()).save(any());
    }

    @Test
    void rejectsBlankFolderName() {
        assertThatThrownBy(() -> driveService.createFolder(
                USER,
                new CreateFolderRequest("   ", null)))
                .isInstanceOf(InvalidDriveRequestException.class)
                .hasMessage("Folder name cannot be blank");
    }

    @Test
    void listsFoldersByOwnedParent() {
        DriveFolder parent = folder(20L, "Parent", null);
        DriveFolder child = folder(21L, "Child", 20L);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(20L, USER_EMAIL))
                .thenReturn(Optional.of(parent));
        when(folderRepository
                .findAllByUserEmailAndParentFolderIdAndDeletedFalseOrderByNameAsc(
                        USER_EMAIL, 20L))
                .thenReturn(List.of(child));

        List<DriveFolderResponse> result = driveService.listFolders(USER, 20L);

        assertThat(result).extracting(DriveFolderResponse::name).containsExactly("Child");
    }

    @Test
    void renamesOwnedFolder() {
        DriveFolder folder = folder(30L, "Old", null);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(30L, USER_EMAIL))
                .thenReturn(Optional.of(folder));
        when(folderRepository.save(folder)).thenReturn(folder);

        DriveFolderResponse result = driveService.renameFolder(
                USER,
                30L,
                new RenameFolderRequest(" New "));

        assertThat(result.name()).isEqualTo("New");
    }

    @Test
    void deleteFolderMovesFolderAndChildFilesToTrash() {
        DriveFolder folder = folder(40L, "Documents", null);
        DriveFile childFile = file(41L);
        childFile.setFolderId(40L);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(40L, USER_EMAIL))
                .thenReturn(Optional.of(folder));
        when(fileRepository.findAllByUserEmailAndFolderId(USER_EMAIL, 40L))
                .thenReturn(List.of(childFile));
        when(folderRepository.findAllByUserEmailAndParentFolderId(USER_EMAIL, 40L))
                .thenReturn(List.of());

        driveService.deleteFolder(USER, 40L);

        assertThat(folder.isDeleted()).isTrue();
        assertThat(childFile.isDeleted()).isTrue();
        verify(fileRepository).saveAll(List.of(childFile));
        verify(folderRepository).save(folder);
    }

    @Test
    void deleteFolderMovesChildFoldersToTrashRecursively() {
        DriveFolder folder = folder(50L, "Parent", null);
        DriveFolder child = folder(51L, "Child", 50L);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(50L, USER_EMAIL))
                .thenReturn(Optional.of(folder));
        when(fileRepository.findAllByUserEmailAndFolderId(USER_EMAIL, 50L))
                .thenReturn(List.of());
        when(folderRepository.findAllByUserEmailAndParentFolderId(USER_EMAIL, 50L))
                .thenReturn(List.of(child));
        when(fileRepository.findAllByUserEmailAndFolderId(USER_EMAIL, 51L))
                .thenReturn(List.of());
        when(folderRepository.findAllByUserEmailAndParentFolderId(USER_EMAIL, 51L))
                .thenReturn(List.of());

        driveService.deleteFolder(USER, 50L);

        assertThat(folder.isDeleted()).isTrue();
        assertThat(child.isDeleted()).isTrue();
        verify(folderRepository).save(child);
        verify(folderRepository).save(folder);
    }

    @Test
    void permanentlyDeletesTrashedFileFromStorageAndMetadata() {
        DriveFile file = file(60L);
        file.setDeleted(true);
        when(fileRepository.findByIdAndUserEmailAndDeletedTrue(60L, USER_EMAIL))
                .thenReturn(Optional.of(file));

        driveService.permanentlyDeleteFile(USER, 60L);

        verify(cloudStorageService).delete(file.getCloudObjectKey());
        verify(shareLinkRepository).deleteAllByFileIdAndOwnerEmail(60L, USER_EMAIL);
        verify(fileRepository).delete(file);
    }

    @Test
    void restoresTrashedFolderTree() {
        DriveFolder folder = folder(70L, "Parent", null);
        DriveFolder child = folder(71L, "Child", 70L);
        DriveFile childFile = file(72L);
        childFile.setFolderId(70L);
        folder.setDeleted(true);
        child.setDeleted(true);
        childFile.setDeleted(true);
        when(folderRepository.findByIdAndUserEmailAndDeletedTrue(70L, USER_EMAIL))
                .thenReturn(Optional.of(folder));
        when(folderRepository.findAllByUserEmailAndParentFolderId(USER_EMAIL, 70L))
                .thenReturn(List.of(child));
        when(folderRepository.findAllByUserEmailAndParentFolderId(USER_EMAIL, 71L))
                .thenReturn(List.of());
        when(fileRepository.findAllByUserEmailAndFolderId(USER_EMAIL, 70L))
                .thenReturn(List.of(childFile));
        when(fileRepository.findAllByUserEmailAndFolderId(USER_EMAIL, 71L))
                .thenReturn(List.of());

        DriveFolderResponse result = driveService.restoreFolder(USER, 70L);

        assertThat(result.id()).isEqualTo(70L);
        assertThat(folder.isDeleted()).isFalse();
        assertThat(child.isDeleted()).isFalse();
        assertThat(childFile.isDeleted()).isFalse();
    }

    @Test
    void softDeletesEmptyFolder() {
        DriveFolder folder = folder(80L, "Empty", null);
        when(folderRepository.findByIdAndUserEmailAndDeletedFalse(80L, USER_EMAIL))
                .thenReturn(Optional.of(folder));
        when(fileRepository.findAllByUserEmailAndFolderId(USER_EMAIL, 80L))
                .thenReturn(List.of());
        when(folderRepository.findAllByUserEmailAndParentFolderId(USER_EMAIL, 80L))
                .thenReturn(List.of());

        driveService.deleteFolder(USER, 80L);

        assertThat(folder.isDeleted()).isTrue();
        verify(folderRepository).save(folder);
    }

    private DriveFile file(Long id) {
        return DriveFile.builder()
                .id(id)
                .userEmail(USER_EMAIL)
                .fileName("document.pdf")
                .originalFileName("document.pdf")
                .contentType("application/pdf")
                .sizeBytes(123L)
                .cloudObjectKey(USER_EMAIL + "/stored-document.pdf")
                .createdAt(Instant.parse("2026-06-19T00:00:00Z"))
                .updatedAt(Instant.parse("2026-06-19T00:00:00Z"))
                .starred(false)
                .deleted(false)
                .build();
    }

    private DriveFolder folder(Long id, String name, Long parentFolderId) {
        return DriveFolder.builder()
                .id(id)
                .userEmail(USER_EMAIL)
                .name(name)
                .parentFolderId(parentFolderId)
                .createdAt(Instant.parse("2026-06-19T00:00:00Z"))
                .updatedAt(Instant.parse("2026-06-19T00:00:00Z"))
                .deleted(false)
                .build();
    }

    private DriveService createService(DriveProperties properties) {
        return new DriveService(
                fileRepository,
                folderRepository,
                permissionRepository,
                shareLinkRepository,
                cloudStorageService,
                properties,
                new DriveAuthorizationService(fileRepository, folderRepository, permissionRepository));
    }
}
