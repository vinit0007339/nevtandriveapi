package com.nevtan.drive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@Getter
@Setter
@ConfigurationProperties(prefix = "drive")
public class DriveShareProperties {

    private String shareBaseUrl = "http://localhost:5173/drive/share";

    public URI validatedBaseUrl() {
        if (shareBaseUrl == null || shareBaseUrl.isBlank()) {
            throw new IllegalStateException("drive.share-base-url is required");
        }
        try {
            URI uri = URI.create(shareBaseUrl);
            if (!uri.isAbsolute()) {
                throw new IllegalStateException("drive.share-base-url must be an absolute URI");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "drive.share-base-url must be a valid absolute URI", exception);
        }
    }
}
