package com.nevtan.drive;

import com.nevtan.drive.config.NevTanCloudProperties;
import com.nevtan.drive.config.ObjectStorageProperties;
import com.nevtan.drive.config.LocalDriveStorageProperties;
import com.nevtan.drive.config.DriveShareProperties;
import com.nevtan.drive.config.DriveProperties;
import com.nevtan.drive.config.DriveCorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@SpringBootApplication
@EnableConfigurationProperties({
        NevTanCloudProperties.class,
        ObjectStorageProperties.class,
        LocalDriveStorageProperties.class,
        DriveShareProperties.class,
        DriveProperties.class,
        DriveCorsProperties.class
})
public class NevTanDriveApiApplication {

    private static final Logger log = LoggerFactory.getLogger(NevTanDriveApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(NevTanDriveApiApplication.class, args);
    }

    @Bean
    ApplicationRunner startupDiagnostics(Environment environment) {
        return (ApplicationArguments args) -> log.info(
                "NevTan Drive API started with profiles={} server.port={}",
                Arrays.toString(environment.getActiveProfiles()),
                environment.getProperty("server.port"));
    }
}
