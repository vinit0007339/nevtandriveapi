package com.nevtan.drive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "nevtan.cloud")
public class NevTanCloudProperties {

    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String bucket;
    private String uploadEndpoint;
    private String downloadEndpoint;
    private String deleteEndpoint;
    private String signedUrlEndpoint;

    public void validateForEnabledIntegration() {
        if (!enabled) {
            return;
        }

        Map<String, String> requiredProperties = new LinkedHashMap<>();
        requiredProperties.put("nevtan.cloud.base-url", baseUrl);
        requiredProperties.put("nevtan.cloud.api-key", apiKey);
        requiredProperties.put("nevtan.cloud.bucket", bucket);
        requiredProperties.put("nevtan.cloud.upload-endpoint", uploadEndpoint);
        requiredProperties.put("nevtan.cloud.download-endpoint", downloadEndpoint);
        requiredProperties.put("nevtan.cloud.delete-endpoint", deleteEndpoint);
        requiredProperties.put("nevtan.cloud.signed-url-endpoint", signedUrlEndpoint);

        String missingProperties = requiredProperties.entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);

        if (missingProperties != null) {
            throw new IllegalStateException(
                    "NevTan Cloud is enabled but required properties are missing: "
                            + missingProperties);
        }

        URI parsedBaseUrl;
        try {
            parsedBaseUrl = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("nevtan.cloud.base-url must be a valid URI", exception);
        }
        if (!parsedBaseUrl.isAbsolute()) {
            throw new IllegalStateException("nevtan.cloud.base-url must be an absolute URI");
        }
    }
}
