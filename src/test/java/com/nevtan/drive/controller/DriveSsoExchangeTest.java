package com.nevtan.drive.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevtan.drive.auth.JwtService;
import com.nevtan.drive.repository.DriveUserRepository;
import com.nevtan.drive.service.CloudStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the CylonCloud-style exchange: an SSO token is introspected, the user
 * is auto-provisioned on first sign-in, and Drive issues its own session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "sso.introspect-url=https://sso.test.local/oauth/introspect")
class DriveSsoExchangeTest {

    private static final String INTROSPECT_URL =
            "https://sso.test.local/oauth/introspect?token=sso-token";

    @TestConfiguration
    static class MockSsoConfiguration {
        @Bean
        MockServerRestClientCustomizer mockServerRestClientCustomizer() {
            return new MockServerRestClientCustomizer();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DriveUserRepository userRepository;

    @Autowired
    private MockServerRestClientCustomizer mockServerCustomizer;

    @MockBean
    private CloudStorageService cloudStorageService;

    private MockRestServiceServer ssoServer;

    @BeforeEach
    void setUp() {
        ssoServer = mockServerCustomizer.getServer();
        ssoServer.reset();
    }

    private void ssoReturns(Map<String, Object> body) throws Exception {
        ssoServer.expect(requestTo(INTROSPECT_URL))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(body), MediaType.APPLICATION_JSON));
    }

    private String exchange() throws Exception {
        return mockMvc.perform(post("/api/drive/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssoToken\":\"sso-token\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void exchangesSsoTokenForDriveSessionAndProvisionsUser() throws Exception {
        String email = "new-user@example.com";
        assertThat(userRepository.findByEmail(email)).isEmpty();

        ssoReturns(Map.of(
                "active", true,
                "sub", "7",
                "email", email,
                "first_name", "Ada",
                "last_name", "Lovelace",
                "username", "ada"));

        String body = exchange();

        assertThat(body).contains("accessToken").contains("refreshToken");
        assertThat(userRepository.findByEmail(email)).isPresent();

        Map<?, ?> session = objectMapper.readValue(body, Map.class);
        String accessToken = (String) session.get("accessToken");
        assertThat(jwtService.extractEmail(accessToken)).isEqualTo(email);
    }

    @Test
    void issuedSessionTokenIsAcceptedByProtectedEndpoints() throws Exception {
        ssoReturns(Map.of("active", true, "sub", "8", "email", "session@example.com"));

        Map<?, ?> session = objectMapper.readValue(exchange(), Map.class);

        mockMvc.perform(get("/api/drive/files")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void doesNotDuplicateUserOnSecondSignIn() throws Exception {
        String email = "returning@example.com";
        for (int attempt = 0; attempt < 2; attempt++) {
            ssoReturns(Map.of("active", true, "sub", "9", "email", email));
            exchange();
            ssoServer.reset();
        }
        assertThat(userRepository.findAll().stream()
                .filter(user -> email.equals(user.getEmail()))
                .count()).isEqualTo(1);
    }

    @Test
    void rejectsInactiveSsoToken() throws Exception {
        ssoReturns(Map.of("active", false));

        mockMvc.perform(post("/api/drive/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssoToken\":\"sso-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSsoTokenWithoutEmail() throws Exception {
        ssoReturns(Map.of("active", true, "sub", "10"));

        mockMvc.perform(post("/api/drive/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssoToken\":\"sso-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsUnreachableSsoAsUnauthorizedWithoutLeakingTheToken() throws Exception {
        ssoServer.expect(requestTo(INTROSPECT_URL)).andRespond(withServerError());

        mockMvc.perform(post("/api/drive/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssoToken\":\"sso-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sso-token"))));
    }

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        ssoReturns(Map.of("active", true, "sub", "11", "email", "refresh@example.com"));
        Map<?, ?> session = objectMapper.readValue(exchange(), Map.class);
        String refreshToken = (String) session.get("refreshToken");

        String refreshed = mockMvc.perform(post("/api/drive/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> next = objectMapper.readValue(refreshed, Map.class);
        assertThat(next.get("refreshToken")).isNotEqualTo(refreshToken);

        // The consumed token must not work a second time.
        mockMvc.perform(post("/api/drive/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesTheRefreshToken() throws Exception {
        ssoReturns(Map.of("active", true, "sub", "12", "email", "logout@example.com"));
        Map<?, ?> session = objectMapper.readValue(exchange(), Map.class);
        String refreshToken = (String) session.get("refreshToken");

        mockMvc.perform(post("/api/drive/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/drive/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRawSsoTokenOnProtectedEndpoints() throws Exception {
        // An SSO token is only valid at the exchange endpoint, never as a session.
        mockMvc.perform(get("/api/drive/files")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer sso-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheSignedInUser() throws Exception {
        ssoReturns(Map.of(
                "active", true, "sub", "13", "email", "me@example.com", "first_name", "Grace"));
        Map<?, ?> session = objectMapper.readValue(exchange(), Map.class);

        mockMvc.perform(get("/api/drive/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.firstName").value("Grace"));
    }
}
