package com.nevtan.drive.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebCorsConfig implements WebMvcConfigurer {

    private final DriveCorsProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/drive/**")
                // Origin patterns rather than exact origins, so deployment
                // hostnames with generated subdomains keep working.
                .allowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(properties.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(properties.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(properties.getExposedHeaders().toArray(String[]::new))
                .maxAge(properties.getMaxAge());
    }
}
