package com.nevtan.drive.service;

import com.nevtan.drive.config.LocalDriveStorageProperties;
import com.nevtan.drive.exception.CloudStorageException;
import com.nevtan.drive.exception.StorageObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMockCloudStorageServiceTest {

    @TempDir
    Path storageRoot;

    private LocalMockCloudStorageService storageService;

    @BeforeEach
    void setUp() {
        LocalDriveStorageProperties properties = new LocalDriveStorageProperties();
        properties.setRoot(storageRoot);
        storageService = new LocalMockCloudStorageService(properties);
    }

    @Test
    void uploadsDownloadsAndDeletesPhysicalFile() throws Exception {
        String objectKey = "user@example.com/test-file.txt";
        byte[] content = "NevTan Drive".getBytes();

        storageService.upload(
                objectKey,
                new ByteArrayInputStream(content),
                content.length,
                "text/plain");

        Path storedFile = storageRoot.resolve(objectKey);
        assertThat(storedFile).exists();
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(content);

        Resource downloaded = storageService.download(objectKey);
        assertThat(downloaded).isInstanceOf(UrlResource.class);
        assertThat(downloaded.getContentAsByteArray()).isEqualTo(content);

        storageService.delete(objectKey);
        assertThat(storedFile).doesNotExist();
    }

    @Test
    void rejectsObjectKeysThatEscapeStorageRoot() {
        assertThatThrownBy(() -> storageService.download("../outside.txt"))
                .isInstanceOf(CloudStorageException.class)
                .hasMessage("Invalid cloud object key");
    }

    @Test
    void reportsMissingPhysicalObjectCleanly() {
        assertThatThrownBy(() -> storageService.download("user@example.com/missing.txt"))
                .isInstanceOf(StorageObjectNotFoundException.class)
                .hasMessage("Stored file content was not found");
    }
}
