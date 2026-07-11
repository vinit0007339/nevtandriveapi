package com.nevtan.drive.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@ConfigurationProperties(prefix = "drive")
public class DriveProperties {

    private long storageLimitBytes = 1_073_741_824L;
    private long maxUploadSizeBytes = 104_857_600L;
    private List<String> blockedExtensions =
            List.of(".exe", ".bat", ".cmd", ".sh", ".js", ".jar");

    @PostConstruct
    void validate() {
        if (storageLimitBytes <= 0) {
            throw new IllegalStateException("drive.storage-limit-bytes must be positive");
        }
        if (maxUploadSizeBytes <= 0) {
            throw new IllegalStateException("drive.max-upload-size-bytes must be positive");
        }
        if (maxUploadSizeBytes > storageLimitBytes) {
            throw new IllegalStateException(
                    "drive.max-upload-size-bytes cannot exceed drive.storage-limit-bytes");
        }
        if (blockedExtensions == null) {
            blockedExtensions = List.of();
        }
    }

    public Set<String> normalizedBlockedExtensions() {
        return blockedExtensions.stream()
                .filter(extension -> extension != null && !extension.isBlank())
                .map(String::trim)
                .map(extension -> extension.startsWith(".") ? extension : "." + extension)
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
