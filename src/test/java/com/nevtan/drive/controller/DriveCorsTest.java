package com.nevtan.drive.controller;

import com.nevtan.drive.service.CloudStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "drive.cors.allowed-origin-patterns=https://*.apps.nevtan.com,http://localhost:5173")
class DriveCorsTest {

    private static final String DEPLOYED_UI =
            "https://drive.nevtan.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CloudStorageService cloudStorageService;

    @Test
    void allowsPreflightFromDeployedUiOrigin() throws Exception {
        mockMvc.perform(options("/api/drive/folders")
                        .header("Origin", DEPLOYED_UI)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", DEPLOYED_UI));
    }

    @Test
    void exposesContentDispositionForDownloads() throws Exception {
        mockMvc.perform(options("/api/drive/folders")
                        .header("Origin", DEPLOYED_UI)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
    }

    @Test
    void rejectsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/drive/folders")
                        .header("Origin", "https://attacker.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
