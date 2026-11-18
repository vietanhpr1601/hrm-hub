package hrm;

import hrm.model.User;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@HrmIntegrationTest
class AiChatIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @Value("${app.admin.username}") String adminUsername;
    private static final String QUESTION = "{\"messages\":[{\"role\":\"user\",\"content\":\"Tổng số nhân viên\"}]}";

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isUnauthorized());
    }
    @Test void checksChatPermissionAndDataPermissionSeparately() throws Exception {
        User user = new User();
        user.setUsername("chat_" + UUID.randomUUID()); user.setPassword("unused"); user.setActive(true);
        user.setPermissionOverrides(new HashMap<>(Map.of("AI_CHAT", "ALLOW")));
        users.saveAndFlush(user);
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + tokens.generateAccessToken(user))
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("Bạn chưa được cấp quyền xem dữ liệu này."))
                .andExpect(jsonPath("$.data.sources").isEmpty());
        user.setPermissionOverrides(new HashMap<>(Map.of("STAFF_VIEW", "ALLOW")));
        users.saveAndFlush(user);
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + tokens.generateAccessToken(user))
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isForbidden());
    }
    @Test void adminCanQueryAndSystemRoleInjectionIsRejected() throws Exception {
        String token = tokens.generateAccessToken(users.findByUsername(adminUsername).orElseThrow());
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("database"))
                .andExpect(jsonPath("$.data.sources[0]").exists()).andExpect(jsonPath("$.data.updatedAt").exists());
        mvc.perform(post("/api/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION.replace("\"user\"", "\"system\"")))
                .andExpect(status().isBadRequest());
    }
}
