package com.nevtan.drive;

import com.nevtan.drive.config.NevTanCloudProperties;
import com.nevtan.drive.config.LocalDriveStorageProperties;
import com.nevtan.drive.config.DriveShareProperties;
import com.nevtan.drive.config.DriveProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        NevTanCloudProperties.class,
        LocalDriveStorageProperties.class,
        DriveShareProperties.class,
        DriveProperties.class
})
public class NevTanDriveApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NevTanDriveApiApplication.class, args);
    }
}
