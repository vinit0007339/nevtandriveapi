package com.nevtan.drive.controller;

import com.nevtan.drive.dto.CreateFolderRequest;
import com.nevtan.drive.dto.CreateDrivePermissionRequest;
import com.nevtan.drive.dto.CreateShareLinkRequest;
import com.nevtan.drive.dto.DriveFileDetailsResponse;
import com.nevtan.drive.dto.DriveFileResponse;
import com.nevtan.drive.dto.DriveFolderResponse;
import com.nevtan.drive.dto.DrivePermissionResponse;
import com.nevtan.drive.dto.DriveTrashResponse;
import com.nevtan.drive.dto.MoveFileRequest;
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadFileResponse> upload(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId
    ) {
        UploadFileResponse uploadedFile = driveService.upload(userEmail, folderId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadedFile);
    }

    @GetMapping("/files")
    public Page<DriveFileResponse> listFiles(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.listFiles(userEmail, folderId, page, size);
    }

    @GetMapping("/recent")
    public Page<DriveFileResponse> listRecentFiles(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.listRecentFiles(userEmail, page, size);
    }

    @GetMapping("/starred")
    public Page<DriveFileResponse> listStarredFiles(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.listStarredFiles(userEmail, page, size);
    }

    @PatchMapping("/files/{fileId}/rename")
    public DriveFileResponse renameFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId,
            @RequestBody RenameFileRequest request
    ) {
        return driveService.renameFile(userEmail, fileId, request);
    }

    @PatchMapping("/files/{fileId}/move")
    public DriveFileResponse moveFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId,
            @RequestBody MoveFileRequest request
    ) {
        return driveService.moveFile(userEmail, fileId, request);
    }

    @PatchMapping("/files/{fileId}/star")
    public DriveFileResponse starFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        return driveService.starFile(userEmail, fileId);
    }

    @PatchMapping("/files/{fileId}/unstar")
    public DriveFileResponse unstarFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        return driveService.unstarFile(userEmail, fileId);
    }

    @GetMapping("/files/{fileId}/details")
    public DriveFileDetailsResponse getFileDetails(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        return driveService.getFileDetails(userEmail, fileId);
    }

    @GetMapping("/search")
    public Page<DriveFileResponse> searchFiles(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return driveService.searchFiles(userEmail, query, page, size);
    }

    @PostMapping("/share/{fileId}")
    public ResponseEntity<ShareLinkResponse> createShareLink(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId,
            @RequestBody(required = false) CreateShareLinkRequest request
    ) {
        ShareLinkResponse response = shareLinkService.create(userEmail, fileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/permissions/{fileId}")
    public ResponseEntity<DrivePermissionResponse> shareFileWithUser(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId,
            @RequestBody CreateDrivePermissionRequest request
    ) {
        DrivePermissionResponse response = permissionService.shareFile(userEmail, fileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/permissions/{fileId}")
    public List<DrivePermissionResponse> listPermissionsForFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        return permissionService.listForFile(userEmail, fileId);
    }

    @DeleteMapping("/permissions/{permissionId}")
    public ResponseEntity<Void> removePermission(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long permissionId
    ) {
        permissionService.removeAccess(userEmail, permissionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared-with-me")
    public List<SharedDriveFileResponse> sharedWithMe(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail
    ) {
        return permissionService.listSharedWithMe(userEmail);
    }

    @GetMapping("/share/file/{fileId}")
    public List<ShareLinkResponse> listShareLinksForFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        return shareLinkService.listActiveForFile(userEmail, fileId);
    }

    @PatchMapping("/share/{shareId}")
    public ShareLinkResponse updateShareLink(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long shareId,
            @RequestBody UpdateShareLinkRequest request
    ) {
        return shareLinkService.update(userEmail, shareId, request);
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
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long shareId
    ) {
        shareLinkService.deactivate(userEmail, shareId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folders")
    public ResponseEntity<DriveFolderResponse> createFolder(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @RequestBody CreateFolderRequest request
    ) {
        DriveFolderResponse folder = driveService.createFolder(userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    @GetMapping("/folders")
    public List<DriveFolderResponse> listFolders(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @RequestParam(required = false) Long parentFolderId
    ) {
        return driveService.listFolders(userEmail, parentFolderId);
    }

    @PatchMapping("/folders/{folderId}")
    public DriveFolderResponse renameFolder(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long folderId,
            @RequestBody RenameFolderRequest request
    ) {
        return driveService.renameFolder(userEmail, folderId, request);
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long folderId
    ) {
        driveService.deleteFolder(userEmail, folderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trash")
    public DriveTrashResponse listTrash(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail
    ) {
        return driveService.listTrash(userEmail);
    }

    @PostMapping("/folders/{folderId}/restore")
    public DriveFolderResponse restoreFolder(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long folderId
    ) {
        return driveService.restoreFolder(userEmail, folderId);
    }

    @DeleteMapping("/folders/{folderId}/permanent")
    public ResponseEntity<Void> permanentlyDeleteFolder(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long folderId
    ) {
        driveService.permanentlyDeleteFolder(userEmail, folderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/storage")
    public StorageUsageResponse getStorageUsage(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail
    ) {
        return driveService.getStorageUsage(userEmail);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        DriveService.DownloadedFile downloadedFile = driveService.download(userEmail, fileId);
        return buildDownloadResponse(
                downloadedFile.resource(),
                downloadedFile.originalFileName(),
                downloadedFile.contentType(),
                downloadedFile.sizeBytes());
    }

    @GetMapping("/preview/{fileId}")
    public ResponseEntity<?> preview(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        DriveService.PreviewFile previewFile = driveService.preview(userEmail, fileId);
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

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> delete(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        driveService.delete(userEmail, fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/files/{fileId}/restore")
    public DriveFileResponse restoreFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        return driveService.restoreFile(userEmail, fileId);
    }

    @DeleteMapping("/files/{fileId}/permanent")
    public ResponseEntity<Void> permanentlyDeleteFile(
            // TODO: Replace userEmail with the authenticated user's principal.
            @RequestParam String userEmail,
            @PathVariable Long fileId
    ) {
        driveService.permanentlyDeleteFile(userEmail, fileId);
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
