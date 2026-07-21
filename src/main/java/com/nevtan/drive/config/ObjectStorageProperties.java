package com.nevtan.drive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for the S3-compatible object storage provider (DigitalOcean
 * Spaces, AWS S3, MinIO, and similar).
 *
 * <p>Selected by setting {@code nevtan.cloud.provider=s3}.
 *
 * <p>Credentials must never be included in logs, exception messages, or object
 * string representations.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nevtan.cloud.s3")
public class ObjectStorageProperties {

    /** Service endpoint, for example {@code https://nyc3.digitaloceanspaces.com}. */
    private String endpoint;

    /** Region identifier, for example {@code nyc3}. */
    private String region;

    /** Bucket (Space) name. */
    private String bucket;

    private String accessKey;

    private String secretKey;

    /** Validity window for generated pre-signed download URLs. */
    private Duration signedUrlExpiry = Duration.ofMinutes(15);

    /**
     * Use path-style addressing ({@code endpoint/bucket/key}) instead of
     * virtual-host addressing ({@code bucket.endpoint/key}). Required by MinIO;
     * DigitalOcean Spaces and AWS S3 support the virtual-host default.
     */
    private boolean pathStyleAccess = false;

    public void validate() {
        Map<String, String> requiredProperties = new LinkedHashMap<>();
        requiredProperties.put("nevtan.cloud.s3.endpoint", endpoint);
        requiredProperties.put("nevtan.cloud.s3.region", region);
        requiredProperties.put("nevtan.cloud.s3.bucket", bucket);
        requiredProperties.put("nevtan.cloud.s3.access-key", accessKey);
        requiredProperties.put("nevtan.cloud.s3.secret-key", secretKey);

        String missingProperties = requiredProperties.entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);

        if (missingProperties != null) {
            throw new IllegalStateException(
                    "S3-compatible storage is enabled but required properties are missing: "
                            + missingProperties);
        }

        URI parsedEndpoint;
        try {
            parsedEndpoint = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "nevtan.cloud.s3.endpoint must be a valid URI", exception);
        }
        if (!parsedEndpoint.isAbsolute()) {
            throw new IllegalStateException("nevtan.cloud.s3.endpoint must be an absolute URI");
        }

        if (signedUrlExpiry == null || signedUrlExpiry.isNegative() || signedUrlExpiry.isZero()) {
            throw new IllegalStateException("nevtan.cloud.s3.signed-url-expiry must be positive");
        }
    }
}
