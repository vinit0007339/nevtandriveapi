package com.nevtan.drive.service;

import com.nevtan.drive.config.ObjectStorageProperties;
import com.nevtan.drive.exception.CloudStorageException;
import com.nevtan.drive.exception.StorageObjectNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;

/**
 * Storage adapter for S3-compatible object stores such as DigitalOcean Spaces.
 *
 * <p>Selected by setting {@code nevtan.cloud.provider=s3}. Requests are signed
 * with AWS SigV4 using the configured access key and secret.
 *
 * <p>Credentials are never included in logs or exception messages.
 */
@Service
@ConditionalOnProperty(prefix = "nevtan.cloud", name = "provider", havingValue = "s3")
public class ObjectStorageService implements CloudStorageService {

    private final ObjectStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public ObjectStorageService(ObjectStorageProperties properties) {
        properties.validate();
        this.properties = properties;

        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        URI endpoint = URI.create(properties.getEndpoint());
        Region region = Region.of(properties.getRegion());

        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build())
                .build();

        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build())
                .build();
    }

    @Override
    public void upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) {
        String key = requireObjectKey(objectKey);
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentLength(contentLength);
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }
        try {
            s3Client.putObject(
                    request.build(),
                    RequestBody.fromInputStream(inputStream, contentLength));
        } catch (S3Exception exception) {
            throw new CloudStorageException("Failed to store file in object storage", exception);
        }
    }

    @Override
    public Resource download(String objectKey) {
        String key = requireObjectKey(objectKey);
        try {
            ResponseInputStream<GetObjectResponse> objectStream = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .build());
            long contentLength = objectStream.response().contentLength();
            return new InputStreamResource(objectStream) {
                @Override
                public long contentLength() {
                    return contentLength;
                }
            };
        } catch (NoSuchKeyException exception) {
            throw new StorageObjectNotFoundException();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new StorageObjectNotFoundException();
            }
            throw new CloudStorageException("Failed to read file from object storage", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        String key = requireObjectKey(objectKey);
        try {
            // S3 delete is idempotent: a missing key still returns success.
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (S3Exception exception) {
            throw new CloudStorageException("Failed to delete file from object storage", exception);
        }
    }

    @Override
    public URI generateDownloadUrl(String objectKey) {
        String key = requireObjectKey(objectKey);
        try {
            return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(properties.getSignedUrlExpiry())
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket(properties.getBucket())
                                    .key(key)
                                    .build())
                            .build())
                    .url()
                    .toURI();
        } catch (Exception exception) {
            throw new CloudStorageException("Failed to generate signed download URL", exception);
        }
    }

    private String requireObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new CloudStorageException("Invalid cloud object key");
        }
        return objectKey;
    }
}
