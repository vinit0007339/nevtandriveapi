package com.nevtan.drive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "drive.cors")
public class DriveCorsProperties {

    /**
     * Origins permitted to call the API from a browser. Entries may contain
     * {@code *} wildcards, for example {@code https://*.apps.nevtan.com}.
     */
    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5174");

    private List<String> allowedMethods = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private List<String> allowedHeaders = List.of("Content-Type", "Authorization");

    /** Response headers the browser is allowed to read, needed for downloads. */
    private List<String> exposedHeaders = List.of("Content-Disposition");

    /** How long a browser may cache the preflight response, in seconds. */
    private long maxAge = 3600;
}
