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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@HrmIntegrationTest
class BatchApprovalAccessTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @Value("${app.admin.username}") String admin;
    @Test void batchEndpointsRequireApprovalPermissionAndRejectEmptyIds() throws Exception {
        User viewer = new User(); viewer.setUsername("batch_" + UUID.randomUUID()); viewer.setPassword("unused"); viewer.setActive(true);
        viewer.setPermissionOverrides(new HashMap<>(Map.of("LEAVE_VIEW", "ALLOW", "ATTENDANCE_VIEW", "ALLOW")));
        users.saveAndFlush(viewer);
        for (String path : List.of("/api/leave-requests/approve-batch", "/api/timesheets/approve-batch")) {
            mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(put(path).header("Authorization", "Bearer " + tokens.generateAccessToken(viewer))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put(path).header("Authorization", "Bearer " + tokens.generateAccessToken(users.findByUsername(admin).orElseThrow()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
