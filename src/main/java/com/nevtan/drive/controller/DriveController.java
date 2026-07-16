package com.nevtan.drive.controller;

import com.nevtan.drive.auth.CurrentUserService;
import com.nevtan.drive.dto.CreateFolderRequest;
import com.nevtan.drive.dto.CreateDrivePermissionRequest;
import com.nevtan.drive.dto.CreateShareLinkRequest;
import com.nevtan.drive.dto.DriveFileDetailsResponse;
import com.nevtan.drive.dto.DriveFileResponse;
import com.nevtan.drive.dto.DriveFolderResponse;
import com.nevtan.drive.dto.DrivePermissionResponse;
import com.nevtan.drive.dto.DriveSharedWithMeResponse;
import com.nevtan.drive.dto.DriveTrashResponse;
import com.nevtan.drive.dto.MoveFileRequest;
import com.nevtan.drive.dto.MoveFolderRequest;
import com.nevtan.drive.dto.PresentationSlidePreviewResponse;
import com.nevtan.drive.dto.RenameFileRequest;
import com.nevtan.drive.dto.RenameFolderRequest;
import com.nevtan.drive.dto.ShareLinkResponse;
import com.nevtan.drive.dto.SharedDriveFileResponse;
import com.nevtan.drive.dto.StorageUsageResponse;
import com.nevtan.drive.dto.UpdateShareLinkRequest;
import com.nevtan.drive.dto.UploadFileResponse;
import com.nevtan.drive.service.DrivePermissionService;
import com.nevtan.drive.service.DriveService;
import com.nevtan.drive.service.DriveShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/drive")
@RequiredArgsConstructor
public class DriveController {

    private final DriveService driveService;
    private final DriveShareLinkService shareLinkService;
    private final DrivePermissionService permissionService;
    private final CurrentUserService currentUserService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId
    ) {
        UploadFileResponse uploadedFile = driveService.upload(currentUserService.currentUser(), folderId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadedFile);
    }

    @GetMapping("/files")
    public Page<DriveFileResponse> listFiles(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.listFiles(currentUserService.currentUser(), folderId, page, size);
    }

    @GetMapping("/recent")
    public Page<DriveFileResponse> listRecentFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.listRecentFiles(currentUserService.currentUser(), page, size);
    }

    @GetMapping("/starred")
    public Page<DriveFileResponse> listStarredFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.listStarredFiles(currentUserService.currentUser(), page, size);
    }

    @PatchMapping("/files/{fileId}/rename")
    public DriveFileResponse renameFile(
            @PathVariable Long fileId,
            @RequestBody RenameFileRequest request
    ) {
        return driveService.renameFile(currentUserService.currentUser(), fileId, request);
    }

    @PutMapping(value = "/files/{fileId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DriveFileResponse updateFileContent(
            @PathVariable Long fileId,
            @RequestParam("file") MultipartFile file
    ) {
        return driveService.updateFileContent(currentUserService.currentUser(), fileId, file);
    }

    @PatchMapping("/files/{fileId}/move")
    public DriveFileResponse moveFile(
            @PathVariable Long fileId,
            @RequestBody MoveFileRequest request
    ) {
        return driveService.moveFile(currentUserService.currentUser(), fileId, request);
    }

    @PatchMapping("/files/{fileId}/star")
    public DriveFileResponse starFile(
            @PathVariable Long fileId
    ) {
        return driveService.starFile(currentUserService.currentUser(), fileId);
    }

    @PatchMapping("/files/{fileId}/unstar")
    public DriveFileResponse unstarFile(
            @PathVariable Long fileId
    ) {
        return driveService.unstarFile(currentUserService.currentUser(), fileId);
    }

    @GetMapping("/files/{fileId}/details")
    public DriveFileDetailsResponse getFileDetails(
            @PathVariable Long fileId
    ) {
        return driveService.getFileDetails(currentUserService.currentUser(), fileId);
    }

    @GetMapping("/search")
    public Page<DriveFileResponse> searchFiles(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.searchFiles(currentUserService.currentUser(), query, page, size);
    }

    @PostMapping("/share/{fileId}")
    public ResponseEntity<ShareLinkResponse> createShareLink(
            @PathVariable Long fileId,
            @RequestBody(required = false) CreateShareLinkRequest request
    ) {
        ShareLinkResponse response = shareLinkService.create(currentUserService.currentUser(), fileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/permissions/{fileId}")
    public ResponseEntity<DrivePermissionResponse> shareFileWithUser(
            @PathVariable Long fileId,
            @RequestBody CreateDrivePermissionRequest request
    ) {
        DrivePermissionResponse response = permissionService.shareFile(currentUserService.currentUser(), fileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/permissions/folder/{folderId}")
    public ResponseEntity<DrivePermissionResponse> shareFolderWithUser(
            @PathVariable Long folderId,
            @RequestBody CreateDrivePermissionRequest request
    ) {
        DrivePermissionResponse response = permissionService.shareFolder(
                currentUserService.currentUser(),
                folderId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/permissions/{fileId}")
    public List<DrivePermissionResponse> listPermissionsForFile(
            @PathVariable Long fileId
    ) {
        return permissionService.listForFile(currentUserService.currentUser(), fileId);
    }

    @GetMapping("/permissions/folder/{folderId}")
    public List<DrivePermissionResponse> listPermissionsForFolder(
            @PathVariable Long folderId
    ) {
        return permissionService.listForFolder(currentUserService.currentUser(), folderId);
    }

    @DeleteMapping("/permissions/{permissionId}")
    public ResponseEntity<Void> removePermission(
            @PathVariable Long permissionId
    ) {
        permissionService.removeAccess(currentUserService.currentUser(), permissionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared-with-me")
    public DriveSharedWithMeResponse sharedWithMe(    ) {
        return permissionService.listSharedWithMe(currentUserService.currentUser());
    }

    @GetMapping("/share/file/{fileId}")
    public List<ShareLinkResponse> listShareLinksForFile(
            @PathVariable Long fileId
    ) {
        return shareLinkService.listActiveForFile(currentUserService.currentUser(), fileId);
    }

    @PatchMapping("/share/{shareId}")
    public ShareLinkResponse updateShareLink(
            @PathVariable Long shareId,
            @RequestBody UpdateShareLinkRequest request
    ) {
        return shareLinkService.update(currentUserService.currentUser(), shareId, request);
    }

    @GetMapping("/share/{token}")
    public ResponseEntity<Resource> downloadSharedFile(@PathVariable String token) {
        DriveShareLinkService.SharedDownload downloadedFile = shareLinkService.download(token);
        return buildDownloadResponse(
                downloadedFile.resource(),
                downloadedFile.originalFileName(),
                downloadedFile.contentType(),
                downloadedFile.sizeBytes());
    }

    @DeleteMapping("/share/{shareId}")
    public ResponseEntity<Void> deleteShareLink(
            @PathVariable Long shareId
    ) {
        shareLinkService.deactivate(currentUserService.currentUser(), shareId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folders")
    public ResponseEntity<DriveFolderResponse> createFolder(
            @RequestBody CreateFolderRequest request
    ) {
        DriveFolderResponse folder = driveService.createFolder(currentUserService.currentUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    @GetMapping("/folders")
    public List<DriveFolderResponse> listFolders(
            @RequestParam(required = false) Long parentFolderId
    ) {
        return driveService.listFolders(currentUserService.currentUser(), parentFolderId);
    }

    @PatchMapping("/folders/{folderId}")
    public DriveFolderResponse renameFolder(
            @PathVariable Long folderId,
            @RequestBody RenameFolderRequest request
    ) {
        return driveService.renameFolder(currentUserService.currentUser(), folderId, request);
    }

    @PatchMapping("/folders/{folderId}/move")
    public DriveFolderResponse moveFolder(
            @PathVariable Long folderId,
            @RequestBody MoveFolderRequest request
    ) {
        return driveService.moveFolder(currentUserService.currentUser(), folderId, request);
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable Long folderId
    ) {
        driveService.deleteFolder(currentUserService.currentUser(), folderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trash")
    public DriveTrashResponse listTrash(    ) {
        return driveService.listTrash(currentUserService.currentUser());
    }

    @PostMapping("/folders/{folderId}/restore")
    public DriveFolderResponse restoreFolder(
            @PathVariable Long folderId
    ) {
        return driveService.restoreFolder(currentUserService.currentUser(), folderId);
    }

    @DeleteMapping("/folders/{folderId}/permanent")
    public ResponseEntity<Void> permanentlyDeleteFolder(
            @PathVariable Long folderId
    ) {
        driveService.permanentlyDeleteFolder(currentUserService.currentUser(), folderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/storage")
    public StorageUsageResponse getStorageUsage(    ) {
        return driveService.getStorageUsage(currentUserService.currentUser());
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long fileId
    ) {
        DriveService.DownloadedFile downloadedFile = driveService.download(currentUserService.currentUser(), fileId);
        return buildDownloadResponse(
                downloadedFile.resource(),
                downloadedFile.originalFileName(),
                downloadedFile.contentType(),
                downloadedFile.sizeBytes());
    }

    @GetMapping("/preview/{fileId}")
    public ResponseEntity<?> preview(
            @PathVariable Long fileId
    ) {
        DriveService.PreviewFile previewFile = driveService.preview(currentUserService.currentUser(), fileId);
        if (!previewFile.previewSupported()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(previewFile.metadata());
        }
        return buildInlineResponse(
                previewFile.resource(),
                previewFile.originalFileName(),
                previewFile.contentType(),
                previewFile.sizeBytes());
    }

    @GetMapping("/preview/{fileId}/slides")
    public PresentationSlidePreviewResponse previewPresentationSlides(
            @PathVariable Long fileId
    ) {
        return driveService.previewPresentationSlides(currentUserService.currentUser(), fileId);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long fileId
    ) {
        driveService.delete(currentUserService.currentUser(), fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/files/{fileId}/restore")
    public DriveFileResponse restoreFile(
            @PathVariable Long fileId
    ) {
        return driveService.restoreFile(currentUserService.currentUser(), fileId);
    }

    @DeleteMapping("/files/{fileId}/permanent")
    public ResponseEntity<Void> permanentlyDeleteFile(
            @PathVariable Long fileId
    ) {
        driveService.permanentlyDeleteFile(currentUserService.currentUser(), fileId);
        return ResponseEntity.noContent().build();
    }

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private ResponseEntity<Resource> buildDownloadResponse(
            Resource resource,
            String originalFileName,
            String contentType,
            long sizeBytes
    ) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(originalFileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(parseMediaType(contentType))
                .contentLength(sizeBytes)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private ResponseEntity<Resource> buildInlineResponse(
            Resource resource,
            String originalFileName,
            String contentType,
            long sizeBytes
    ) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(originalFileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(parseMediaType(contentType))
                .contentLength(sizeBytes)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
