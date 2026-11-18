package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.Role;
import hrm.model.User;
import hrm.model.UserRole;
import hrm.repository.RoleRepository;
import hrm.repository.StaffRepository;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import hrm.security.PermissionPolicy;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses real JWTs and database accounts; a client cannot nominate a different confirmation account. */
@HrmIntegrationTest
class StaffExcelConfirmationTests {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String EXCEL_ACCEPT = XLSX + ", application/json";
    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired StaffRepository staffs;
    @Autowired JwtTokenProvider tokens;
    @Autowired PasswordEncoder passwords;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void browserDownloadAcceptReturnsJsonPasswordErrorsAndThenXlsxWithTheSameSession() throws Exception {
        User exporter = account("BrowserExporterPassword123!", "STAFF_EXPORT");
        String jwt = jwt(exporter);
        for (String confirmation : new String[]{null, "WrongBrowserPassword123!"}) {
            MockHttpServletRequestBuilder request = post("/api/staffs/export-excel")
                    .header(HttpHeaders.ACCEPT, EXCEL_ACCEPT)
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
            if (confirmation != null) request.header("X-Confirmation-Password", encoded(confirmation));
            MockHttpServletResponse failure = perform(jwt, request, 400);
            assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(failure.getContentType())));
            assertNull(failure.getHeader(HttpHeaders.CONTENT_DISPOSITION));
            assertTrue(mapper.readTree(failure.getContentAsByteArray()).path("message").asText()
                    .toLowerCase(Locale.ROOT).contains("mật khẩu"));
        }
        perform(jwt, get("/api/auth/getCurrentUser"), 200);
        MockHttpServletResponse success = perform(jwt, export("BrowserExporterPassword123!")
                .header(HttpHeaders.ACCEPT, EXCEL_ACCEPT)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 200);
        assertEquals(XLSX, success.getContentType());
        assertNotNull(success.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(success.getContentAsByteArray()))) {
            assertNotNull(workbook.getSheet("Nhan vien"));
        }
    }

    @Test
    void browserTemplateAcceptSupportsJsonDenialsAndAdminXlsxDownload() throws Exception {
        User ordinary = account("BrowserTemplateUserPassword123!", "STAFF_VIEW");
        MockHttpServletResponse failure = perform(jwt(ordinary), get("/api/staffs/import-template")
                .header(HttpHeaders.ACCEPT, EXCEL_ACCEPT), 403);
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(failure.getContentType())));
        assertNull(failure.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertFalse(mapper.readTree(failure.getContentAsByteArray()).path("message").asText().isBlank());
        User admin = administrator("BrowserTemplateAdminPassword123!");
        MockHttpServletResponse success = perform(jwt(admin), get("/api/staffs/import-template")
                .header(HttpHeaders.ACCEPT, EXCEL_ACCEPT), 200);
        assertEquals(XLSX, success.getContentType());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(success.getContentAsByteArray()))) {
            assertNotNull(workbook.getSheet("Nhan vien"));
        }
    }

    @Test
    void allowedExporterNeedsOwnPasswordForEveryExport() throws Exception {
        User exporter = account("ExporterPassword123!", "STAFF_EXPORT");
        String jwt = jwt(exporter);
        for (String password : new String[]{null, "", "IncorrectPassword123!"}) {
            MockHttpServletRequestBuilder request = post("/api/staffs/export-excel");
            if (password != null) request.header("X-Confirmation-Password", encoded(password));
            MockHttpServletResponse response = perform(jwt, request, 400);
            assertNull(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
            assertNotEquals(XLSX, response.getContentType());
            assertFalse(response.getContentAsString(StandardCharsets.UTF_8).contains("IncorrectPassword123!"));
        }
        MockHttpServletResponse workbook = perform(jwt, export("ExporterPassword123!"), 200);
        assertEquals(XLSX, workbook.getContentType());
        try (XSSFWorkbook excel = new XSSFWorkbook(new ByteArrayInputStream(workbook.getContentAsByteArray()))) {
            assertNotNull(excel.getSheet("Nhan vien"));
        }
        perform(jwt, post("/api/staffs/export-excel"), 400);
    }

    @Test
    void passwordDoesNotGrantExportPermissionAndAnotherAccountsPasswordDoesNotConfirm() throws Exception {
        User viewer = account("ViewerPassword123!", "STAFF_VIEW");
        perform(jwt(viewer), export("ViewerPassword123!"), 403);
        User other = account("OtherPassword123!", "STAFF_EXPORT");
        User exporter = account("OwnPassword123!", "STAFF_EXPORT");
        perform(jwt(exporter), export("OtherPassword123!")
                .header("X-Confirmation-Username", other.getUsername()).param("username", other.getUsername()), 400);
        perform(jwt(exporter), export("OwnPassword123!"), 200);
    }

    @Test
    void confirmationUsesCurrentDatabasePasswordAfterPasswordChanges() throws Exception {
        User exporter = account("PreviousPassword123!", "STAFF_EXPORT");
        String jwt = jwt(exporter);
        User saved = users.findById(exporter.getId()).orElseThrow();
        saved.setPassword(passwords.encode("UpdatedPassword123!"));
        entityManager.flush();
        entityManager.clear();
        perform(jwt, export("PreviousPassword123!"), 400);
        perform(jwt, export("UpdatedPassword123!"), 200);
    }

    @Test
    void encodedUnicodeSpacesAndPercentSignsRemainExactForExportAndMultipartImport() throws Exception {
        String password = "  Mật khẩu + 100%  ";
        User admin = administrator(password);
        String jwt = jwt(admin);
        perform(jwt, export(password), 200);
        perform(jwt, export(password.trim()), 400);
        perform(jwt, post("/api/staffs/export-excel").header("X-Confirmation-Password", "%ZZ"), 400);
        perform(jwt, post("/api/staffs/export-excel").header("X-Confirmation-Password", encoded(encoded(password))), 400);
        long count = staffs.count();
        MockHttpServletResponse response = perform(jwt, multipart("/api/staffs/import-excel")
                .file(employeeFile()).param("password", password), 200);
        assertEquals(1, mapper.readTree(response.getContentAsByteArray()).path("data").path("importedCount").asInt());
        assertEquals(count + 1, staffs.count());
    }

    @Test
    void adminImportRejectsMissingWrongAndOtherAccountsPasswordWithoutSaving() throws Exception {
        User admin = administrator("AdminOwnPassword123!");
        User other = account("AnotherPassword123!", "STAFF_EXPORT");
        String jwt = jwt(admin);
        long count = staffs.count();
        for (String password : new String[]{null, "", "AnotherPassword123!"}) {
            var request = multipart("/api/staffs/import-excel").file(employeeFile())
                    .param("username", other.getUsername());
            if (password != null) request.param("password", password);
            MockHttpServletResponse response = perform(jwt, request, 400);
            assertTrue(mapper.readTree(response.getContentAsByteArray()).path("message").asText().contains("mật khẩu"));
        }
        assertEquals(count, staffs.count());
        perform(jwt, get("/api/staffs/import-template"), 200);
    }

    @Test
    void oldRoleAndAllowOverrideCannotGrantImportToNonAdmin() throws Exception {
        User account = account("NonAdminPassword123!", "STAFF_IMPORT", "STAFF_EXPORT");
        account.getPermissionOverrides().put("STAFF_IMPORT", "ALLOW");
        String jwt = jwt(account);
        User persisted = users.findById(account.getId()).orElseThrow();
        assertFalse(PermissionPolicy.groupPermissions(persisted).contains("STAFF_IMPORT"));
        assertFalse(PermissionPolicy.effective(persisted).contains("STAFF_IMPORT"));
        assertTrue(PermissionPolicy.effective(persisted).contains("STAFF_EXPORT"));
        perform(jwt, get("/api/staffs/import-template"), 403);
        long count = staffs.count();
        perform(jwt, multipart("/api/staffs/import-excel").file(employeeFile())
                .param("password", "NonAdminPassword123!"), 403);
        assertEquals(count, staffs.count());
        perform(jwt, export("NonAdminPassword123!"), 200);
    }

    @Test
    void importCannotBeAddedToCustomGroupsOrUserOverridesAndCatalogIdentifiesAdminOnly() throws Exception {
        User admin = administrator("CatalogAdminPassword123!");
        User ordinary = account("OrdinaryPassword123!", "STAFF_VIEW");
        ordinary.getPermissionOverrides().put("STAFF_IMPORT", "ALLOW");
        UUID groupId = ordinary.getUserRoles().iterator().next().getRole().getId();
        String jwt = jwt(admin);
        perform(jwt, json(post("/api/access-control/roles"), Map.of("name", code(), "description", "Restricted importer",
                "permissions", Set.of("STAFF_IMPORT"))), 400);
        perform(jwt, json(put("/api/access-control/roles/" + groupId + "/permissions"), Map.of("permissions", Set.of("STAFF_IMPORT"))), 400);
        perform(jwt, json(put("/api/access-control/users/" + ordinary.getId()), Map.of("roleIds", Set.of(groupId),
                "permissionOverrides", Map.of("STAFF_IMPORT", "ALLOW"))), 400);
        JsonNode catalog = mapper.readTree(perform(jwt, get("/api/access-control/catalog"), 200).getContentAsByteArray()).path("data");
        boolean found = false;
        for (JsonNode item : catalog) if (item.path("code").asText().equals("STAFF_IMPORT")) {
            assertTrue(item.path("adminOnly").asBoolean());
            found = true;
        }
        assertTrue(found);
        JsonNode access = mapper.readTree(perform(jwt, get("/api/access-control/users/" + ordinary.getId()), 200).getContentAsByteArray()).path("data");
        assertFalse(access.path("permissionOverrides").has("STAFF_IMPORT"));
        assertTrue(PermissionPolicy.has(users.findById(admin.getId()).orElseThrow(), "STAFF_IMPORT"));
    }

    private User administrator(String password) {
        return createAccount(password, roles.findByName("ROLE_ADMIN").orElseThrow());
    }
    private User account(String password, String... permissions) {
        Role role = new Role();
        role.setName(code());
        role.setPermissionsConfigured(true);
        role.setPermissions(new HashSet<>(List.of(permissions)));
        entityManager.persist(role);
        return createAccount(password, role);
    }
    private User createAccount(String password, Role role) {
        User account = new User();
        account.setUsername(code());
        account.setPassword(passwords.encode(password));
        UserRole link = new UserRole();
        link.setRole(role);
        link.setUser(account);
        account.getUserRoles().add(link);
        entityManager.persist(account);
        return account;
    }
    private String jwt(User account) {
        entityManager.flush();
        String jwt = tokens.generateAccessToken(account);
        entityManager.clear();
        return jwt;
    }
    private MockHttpServletResponse perform(String jwt, org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> request, int expected) throws Exception {
        SecurityContextHolder.clearContext();
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().is(expected)).andReturn().getResponse();
    }
    private MockHttpServletRequestBuilder export(String password) {
        return post("/api/staffs/export-excel").header("X-Confirmation-Password", encoded(password));
    }
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }
    private static String encoded(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String code() { return "EXCEL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    private MockMultipartFile employeeFile() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Nhan vien");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Họ và tên");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Trạng thái nhân viên");
            Row employee = sheet.createRow(1);
            employee.createCell(0).setCellValue("Nhân viên kiểm thử xác nhận mật khẩu");
            employee.createCell(1).setCellValue(code() + "@example.test");
            employee.createCell(2).setCellValue("7");
            workbook.write(output);
            return new MockMultipartFile("file", "nhan-vien.xlsx", XLSX, output.toByteArray());
        }
    }
}
