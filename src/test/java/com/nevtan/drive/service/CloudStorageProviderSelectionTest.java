package com.nevtan.drive.service;

import com.nevtan.drive.config.LocalDriveStorageProperties;
import com.nevtan.drive.config.NevTanCloudProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class CloudStorageProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageTestConfiguration.class);

    @Test
    void usesLocalMockStorageByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CloudStorageService.class);
            assertThat(context.getBean(CloudStorageService.class))
                    .isInstanceOf(LocalMockCloudStorageService.class);
        });
    }

    @Test
    void usesNevTanCloudStorageWhenEnabledAndConfigured() {
        contextRunner
                .withPropertyValues(
                        "nevtan.cloud.enabled=true",
                        "nevtan.cloud.base-url=https://cloud.example.test",
                        "nevtan.cloud.api-key=test-secret",
                        "nevtan.cloud.bucket=drive",
                        "nevtan.cloud.upload-endpoint=/upload",
                        "nevtan.cloud.download-endpoint=/download",
                        "nevtan.cloud.delete-endpoint=/delete",
                        "nevtan.cloud.signed-url-endpoint=/signed-url")
                .run(context -> {
                    assertThat(context).hasSingleBean(CloudStorageService.class);
                    assertThat(context.getBean(CloudStorageService.class))
                            .isInstanceOf(NevTanCloudStorageService.class);
                });
    }

    @Test
    void failsFastWhenCloudIsEnabledWithoutRequiredProperties() {
        contextRunner
                .withPropertyValues("nevtan.cloud.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("nevtan.cloud.base-url")
                            .hasMessageContaining("nevtan.cloud.api-key")
                            .hasMessageNotContaining("test-secret");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            NevTanCloudProperties.class,
            LocalDriveStorageProperties.class
    })
    @Import({
            LocalMockCloudStorageService.class,
            NevTanCloudStorageService.class
    })
    static class StorageTestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
