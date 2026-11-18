package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.User;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
class HrmApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginProfileRefreshAndLogoutFlow() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"integration-admin\",\"password\":\"IntegrationTestPassword123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = objectMapper.readTree(body).get("data");
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();
        assertNotEquals(accessToken, refreshToken);

        mvc.perform(get("/api/auth/getCurrentUser").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("integration-admin"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        String refreshedBody = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn().getResponse().getContentAsString();
        String refreshedAccessToken = objectMapper.readTree(refreshedBody).path("data").path("accessToken").asText();
        mvc.perform(get("/api/auth/getCurrentUser").header("Authorization", "Bearer " + refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("integration-admin"));

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturnsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"integration-admin\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingLoginFieldsReturnBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousProfileReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/getCurrentUser")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotAuthenticateApiRequests() throws Exception {
        String refreshToken = tokenProvider.generateRefreshToken(admin());
        mvc.perform(get("/api/auth/getCurrentUser").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenCannotBeUsedToRefresh() throws Exception {
        String accessToken = tokenProvider.generateAccessToken(admin());
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotUsePreviouslyIssuedAccessToken() throws Exception {
        User user = admin();
        String accessToken = tokenProvider.generateAccessToken(user);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mvc.perform(get("/api/auth/getCurrentUser").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotRefreshPreviouslyIssuedToken() throws Exception {
        User user = admin();
        String refreshToken = tokenProvider.generateRefreshToken(user);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedRefreshTokenReturnsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid.jwt.token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        String token = tokenProvider.generateRefreshToken(admin());
        int signatureOffset = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureOffset) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureOffset) + replacement + token.substring(signatureOffset + 1);
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tampered + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountCannotUseExistingAccessOrRefreshToken() throws Exception {
        User user = admin();
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);
        user.setIsDeleted(true);
        userRepository.saveAndFlush(user);

        mvc.perform(get("/api/auth/getCurrentUser").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void frontendCanPreflightLogin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private User admin() {
        return userRepository.findByUsername("integration-admin").orElseThrow();
    }
}
