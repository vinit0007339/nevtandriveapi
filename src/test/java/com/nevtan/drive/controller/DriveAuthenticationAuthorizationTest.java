package com.nevtan.drive.controller;

import com.nevtan.drive.auth.JwtService;
import com.nevtan.drive.entity.DriveFile;
import com.nevtan.drive.entity.DrivePermission;
import com.nevtan.drive.entity.DrivePermissionRole;
import com.nevtan.drive.repository.DriveFileRepository;
import com.nevtan.drive.repository.DriveFolderRepository;
import com.nevtan.drive.repository.DrivePermissionRepository;
import com.nevtan.drive.repository.DriveShareLinkRepository;
import com.nevtan.drive.service.CloudStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DriveAuthenticationAuthorizationTest {

    private static final String OWNER = "owner@example.com";
    private static final String OTHER = "other@example.com";
    private static final String VIEWER = "viewer@example.com";
    private static final String EDITOR = "editor@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DriveFileRepository fileRepository;

    @Autowired
    private DriveFolderRepository folderRepository;

    @Autowired
    private DrivePermissionRepository permissionRepository;

    @Autowired
    private DriveShareLinkRepository shareLinkRepository;

    @MockBean
    private CloudStorageService cloudStorageService;

    private DriveFile ownerFile;
    private DriveFile privateFile;

    @BeforeEach
    void setUp() {
        shareLinkRepository.deleteAll();
        permissionRepository.deleteAll();
        fileRepository.deleteAll();
        folderRepository.deleteAll();

        ownerFile = fileRepository.save(file(OWNER, "owner/object.txt", false));
        privateFile = fileRepository.save(file(OTHER, "other/object.txt", false));

        when(cloudStorageService.download(anyString()))
                .thenReturn(new ByteArrayResource("file".getBytes()));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/drive/files"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanAccessOwnFile() throws Exception {
        mockMvc.perform(get("/api/drive/files/{fileId}/details", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER)))
                .andExpect(status().isOk());
    }

    @Test
    void privateFileCannotBeAccessedByChangingId() throws Exception {
        mockMvc.perform(get("/api/drive/files/{fileId}/details", privateFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCanPreviewAndDownloadButCannotMutateOrShare() throws Exception {
        shareWith(VIEWER, DrivePermissionRole.VIEWER);

        mockMvc.perform(get("/api/drive/preview/{fileId}", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/drive/download/{fileId}", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/drive/files/{fileId}/rename", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"new.txt\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/drive/files/{fileId}/move", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\":null}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/drive/permissions/{fileId}", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(VIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharedWithEmail\":\"new@example.com\",\"role\":\"viewer\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/drive/files/{fileId}", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/drive/files/{fileId}/permanent", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(VIEWER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void editorCanEditButCannotPerformOwnerOnlyActions() throws Exception {
        shareWith(EDITOR, DrivePermissionRole.EDITOR);

        mockMvc.perform(patch("/api/drive/files/{fileId}/rename", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"new.txt\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/drive/files/{fileId}/move", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\":null}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/drive/permissions/{fileId}", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharedWithEmail\":\"new@example.com\",\"role\":\"viewer\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/drive/files/{fileId}", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(EDITOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyOwnerCanManageSharingAndPermanentlyDelete() throws Exception {
        DriveFile trashed = fileRepository.save(file(OWNER, "owner/trashed.txt", true));

        mockMvc.perform(post("/api/drive/permissions/{fileId}", ownerFile.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharedWithEmail\":\"new@example.com\",\"role\":\"viewer\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/drive/files/{fileId}/permanent", trashed.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OWNER)))
                .andExpect(status().isNoContent());
    }

    private void shareWith(String email, DrivePermissionRole role) {
        permissionRepository.save(DrivePermission.builder()
                .fileId(ownerFile.getId())
                .ownerEmail(OWNER)
                .sharedWithEmail(email)
                .role(role)
                .build());
    }

    private DriveFile file(String ownerEmail, String objectKey, boolean deleted) {
        Instant now = Instant.now();
        return DriveFile.builder()
                .userEmail(ownerEmail)
                .fileName("document.txt")
                .originalFileName("document.txt")
                .contentType("text/plain")
                .sizeBytes(4L)
                .cloudObjectKey(objectKey)
                .createdAt(now)
                .updatedAt(now)
                .deleted(deleted)
                .build();
    }

    /** A Drive session token, as issued after an SSO exchange. */
    private String bearer(String email) {
        return "Bearer " + jwtService.generateToken("1", email);
    }
}
