package com.nevtan.drive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Getter
@Setter
@ConfigurationProperties(prefix = "drive.local-storage")
public class LocalDriveStorageProperties {

    private Path root = Path.of("./local-drive-storage");
}
