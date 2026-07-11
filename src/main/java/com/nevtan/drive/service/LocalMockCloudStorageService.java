package com.nevtan.drive.service;

import com.nevtan.drive.config.LocalDriveStorageProperties;
import com.nevtan.drive.exception.CloudStorageException;
import com.nevtan.drive.exception.StorageObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(
        prefix = "nevtan.cloud",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
@RequiredArgsConstructor
public class LocalMockCloudStorageService implements CloudStorageService {

    private final LocalDriveStorageProperties properties;

    @Override
    public void upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) {
        Path target = resolveObjectKey(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new CloudStorageException("Failed to store file in local mock storage", exception);
        }
    }

    @Override
    public Resource download(String objectKey) {
        Path target = resolveObjectKey(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new StorageObjectNotFoundException();
        }
        try {
            Path realRoot = storageRoot().toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realRoot)) {
                throw new CloudStorageException("Invalid cloud object key");
            }
            return new UrlResource(realTarget.toUri());
        } catch (IOException exception) {
            throw new StorageObjectNotFoundException();
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolveObjectKey(objectKey);
        try {
            Files.deleteIfExists(target);
            deleteEmptyUserDirectory(target.getParent());
        } catch (IOException exception) {
            throw new CloudStorageException("Failed to delete file from local mock storage", exception);
        }
    }

    @Override
    public URI generateDownloadUrl(String objectKey) {
        return resolveObjectKey(objectKey).toUri();
    }

    private Path resolveObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new CloudStorageException("Invalid cloud object key");
        }
        Path root = storageRoot();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new CloudStorageException("Invalid cloud object key");
        }
        return target;
    }

    private Path storageRoot() {
        return properties.getRoot().toAbsolutePath().normalize();
    }

    private void deleteEmptyUserDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }
}
