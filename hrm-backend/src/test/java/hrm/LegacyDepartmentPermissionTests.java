package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.Department;
import hrm.model.Staff;
import hrm.model.User;
import hrm.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upgrade regression: an obsolete table left in a database must not grant permissions. */
@HrmIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LegacyDepartmentPermissionTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        // H2 DDL commits implicitly, so this fixture must not share other tests' database.
        properties.add("spring.datasource.url", () ->
                "jdbc:h2:mem:hrm-legacy-dept-permissions;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR,MONTH");
        properties.add("spring.datasource.username", () -> "sa");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;

    @Test
    void legacyDepartmentGrantRowsDoNotAuthorizeLinkedAccount() throws Exception {
        jdbc.execute("create table tbl_department_permission (department_id uuid not null, permission varchar(255) not null)");
        try {
            Department department = new Department();
            department.setCode("LEGACY_" + UUID.randomUUID());
            department.setName("Legacy department permission fixture");
            em.persist(department);
            Staff staff = new Staff();
            staff.setStaffCode("LEGACY_" + UUID.randomUUID());
            staff.setDisplayName("Legacy permission employee");
            staff.setDepartment(department);
            em.persist(staff);
            User account = new User();
            account.setUsername("legacy-permission-" + UUID.randomUUID());
            account.setPassword("unused-test-password");
            account.setStaff(staff);
            em.persist(account);
            em.flush();
            jdbc.update("insert into tbl_department_permission (department_id, permission) values (?, ?)",
                    department.getId(), "STAFF_VIEW");
            assertEquals(1, jdbc.queryForObject("select count(*) from tbl_department_permission where department_id = ?",
                    Integer.class, department.getId()));
            String jwt = tokens.generateAccessToken(account);
            em.clear();
            SecurityContextHolder.clearContext();

            mvc.perform(get("/api/staffs").header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isForbidden());
            SecurityContextHolder.clearContext();
            byte[] body = mvc.perform(get("/api/auth/getCurrentUser").header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            JsonNode currentUser = new ObjectMapper().readTree(body).path("data");
            assertTrue(currentUser.path("permissions").isArray());
            assertTrue(currentUser.path("permissions").isEmpty());
            assertTrue(currentUser.path("roles").isEmpty());
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.execute("drop table if exists tbl_department_permission");
        }
    }
}
