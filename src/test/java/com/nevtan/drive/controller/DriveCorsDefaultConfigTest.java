package com.nevtan.drive.controller;

import com.nevtan.drive.service.CloudStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the shipped defaults in application.properties, with no profile and
 * no property overrides, so the deployed origins keep working even when the
 * prod profile is not activated at startup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DriveCorsDefaultConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CloudStorageService cloudStorageService;

    @ParameterizedTest
    @ValueSource(strings = {
            "https://drive.nevtan.com",
            "https://react-nevtandriveui-6a5f7cc7.apps.nevtan.com",
            "http://localhost:5173"
    })
    void allowsPreflightWithDefaultConfiguration(String origin) throws Exception {
        mockMvc.perform(options("/api/drive/files")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void stillRejectsUnknownOriginWithDefaultConfiguration() throws Exception {
        mockMvc.perform(options("/api/drive/files")
                        .header("Origin", "https://attacker.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
