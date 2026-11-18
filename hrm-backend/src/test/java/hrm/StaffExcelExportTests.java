package hrm;

import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.Department;
import hrm.model.Position;
import hrm.model.Staff;
import hrm.repository.StaffRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
@WithMockUser(username = "integration-admin", authorities = "ROLE_ADMIN")
class StaffExcelExportTests {
    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] HEADERS = {"STT", "Mã nhân viên", "Họ và tên", "Ngày sinh", "Giới tính",
            "Số điện thoại", "Email", "Ngày vào làm", "Trạng thái nhân viên", "Phòng ban", "Vị trí",
            "Số CCCD/CMND", "Địa chỉ hiện tại", "Mã số BHXH"};

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;
    @Autowired private StaffRepository staffRepository;
    @Value("${app.admin.password}") private String confirmationPassword;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void responseIsReadableXlsxAndPreservesVietnameseDatesAndTextIdentifiers() throws Exception {
        Department department = department("Phòng Nhân sự & Hành chính");
        Position position = new Position();
        position.setCode(uniqueCode());
        position.setName("Chuyên viên nhân sự");
        position.setDepartment(department);
        entityManager.persist(position);

        Staff employee = staff("0000123", "Nguyễn Thị Ánh", department);
        employee.setBirthDate(LocalDate.of(1995, 2, 3));
        employee.setStartDate(LocalDate.of(2026, 9, 1));
        employee.setGender("FEMALE");
        employee.setPhoneNumber("0901234567");
        employee.setEmail("anh@example.test");
        employee.setWorkingStatus("7");
        employee.setPosition(position);
        employee.setIdNumber("001095000012");
        employee.setCurrentAddress("12 Nguyễn Huệ, Thành phố Hồ Chí Minh");
        employee.setSocialInsuranceCode("0012345678");
        staffRepository.save(employee);

        Staff formulaLike = staff("0000124", "=HYPERLINK(\"https://example.test\",\"Nhân viên\")", department);
        formulaLike.setCurrentAddress("+SUM(1,2)");
        staffRepository.save(formulaLike);
        Staff minimal = staff("0000125", "Trần Văn Bình", department);
        staffRepository.save(minimal);
        flushAndClear();

        MockHttpServletResponse response = export(Map.of("departmentId", department.getId().toString()));
        assertEquals(XLSX_TYPE, response.getContentType());
        ContentDisposition disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("attachment", disposition.getType());
        assertEquals("Danh_sach_nhan_vien.xlsx", disposition.getFilename());
        assertTrue(response.getHeaders(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS).stream()
                .anyMatch(value -> value.toLowerCase().contains("content-disposition")));
        byte[] bytes = response.getContentAsByteArray();
        assertTrue(bytes.length > 4);
        assertArrayEquals(new byte[]{'P', 'K', 3, 4}, java.util.Arrays.copyOf(bytes, 4));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheet("Nhan vien");
            assertNotNull(sheet);
            assertHeaders(sheet);
            assertEquals(3, sheet.getLastRowNum());

            Row row = rowForCode(sheet, "0000123");
            assertText(row, 1, "0000123");
            assertText(row, 2, "Nguyễn Thị Ánh");
            assertDate(row.getCell(3), LocalDate.of(1995, 2, 3), "03/02/1995");
            assertText(row, 4, "Nữ");
            assertText(row, 5, "0901234567");
            assertText(row, 6, "anh@example.test");
            assertDate(row.getCell(7), LocalDate.of(2026, 9, 1), "01/09/2026");
            assertText(row, 8, "Đang làm việc");
            assertText(row, 9, "Phòng Nhân sự & Hành chính");
            assertText(row, 10, "Chuyên viên nhân sự");
            assertText(row, 11, "001095000012");
            assertText(row, 12, "12 Nguyễn Huệ, Thành phố Hồ Chí Minh");
            assertText(row, 13, "0012345678");

            Row formulaRow = rowForCode(sheet, "0000124");
            assertText(formulaRow, 2, formulaLike.getDisplayName());
            assertText(formulaRow, 12, "+SUM(1,2)");
            for (Row workbookRow : sheet) {
                for (Cell cell : workbookRow) {
                    assertNotEquals(CellType.FORMULA, cell.getCellType(), "Employee text must never become an Excel formula");
                }
            }

            Row minimalRow = rowForCode(sheet, "0000125");
            DataFormatter formatter = new DataFormatter();
            for (int column : new int[]{3, 4, 5, 6, 7, 8, 10, 11, 12, 13}) {
                assertEquals("", formatter.formatCellValue(minimalRow.getCell(column)), "Missing optional field at column " + column);
            }
        }

        // The exact HTTP response is retained for opening in Microsoft Excel; all employees above are isolated test data.
        Path verification = Path.of("target", "staff-export-verification", "staff-export-test.xlsx");
        Files.createDirectories(verification.getParent());
        Files.write(verification, bytes);
    }

    @Test
    void exportRespectsKeywordAndDepartmentButIncludesAllMatchingPagesAndExcludesDeletedStaff() throws Exception {
        String keyword = uniqueCode();
        Department selected = department("Phòng xuất dữ liệu");
        Department other = department("Phòng khác");
        staff(keyword + "_001", "Nhân viên thứ nhất", selected);
        staff(keyword + "_002", "Nhân viên thứ hai", selected);
        Staff deleted = staff(keyword + "_003", "Đã xóa", selected);
        deleted.setIsDeleted(true);
        staffRepository.save(deleted);
        staff(keyword + "_004", "Khác phòng ban", other);
        staff(uniqueCode(), "Không khớp từ khóa", selected);
        flushAndClear();

        MockHttpServletResponse response = export(Map.of("keyword", keyword.toLowerCase(),
                "departmentId", selected.getId().toString(), "pageIndex", 2, "pageSize", 1));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(2, sheet.getLastRowNum());
            Set<String> exportedCodes = new HashSet<>();
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                exportedCodes.add(sheet.getRow(row).getCell(1).getStringCellValue());
            }
            assertEquals(Set.of(keyword + "_001", keyword + "_002"), exportedCodes);
        }
    }

    @Test
    void noMatchesStillReturnsValidWorkbookWithColumnHeaders() throws Exception {
        MockHttpServletResponse response = export(Map.of("keyword", uniqueCode()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertHeaders(sheet);
            assertEquals(0, sheet.getLastRowNum());
            assertEquals(1, sheet.getPhysicalNumberOfRows());
        }
    }

    @Test
    void exportWithoutRequestBodyIncludesActiveStaff() throws Exception {
        String code = uniqueCode();
        staff(code, "Nhân viên không lọc", null);
        flushAndClear();
        MockHttpServletResponse response = mvc.perform(post("/api/staffs/export-excel").header("X-Confirmation-Password", confirmationPassword))
                .andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertNotNull(rowForCode(workbook.getSheetAt(0), code));
        }
    }

    @Test
    @WithAnonymousUser
    void anonymousClientCannotDownloadStaffData() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/staffs/export-excel").header("X-Confirmation-Password", confirmationPassword)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertNotEquals(XLSX_TYPE, response.getContentType());
        assertNull(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
    }

    private MockHttpServletResponse export(Map<String, Object> search) throws Exception {
        return mvc.perform(post("/api/staffs/export-excel").header("X-Confirmation-Password", confirmationPassword).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    private Department department(String name) {
        Department department = new Department();
        department.setCode(uniqueCode());
        department.setName(name);
        entityManager.persist(department);
        return department;
    }

    private Staff staff(String code, String name, Department department) {
        Staff staff = new Staff();
        staff.setStaffCode(code);
        staff.setDisplayName(name);
        staff.setDepartment(department);
        return staffRepository.save(staff);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertHeaders(Sheet sheet) {
        assertEquals(HEADERS.length, sheet.getRow(0).getLastCellNum());
        for (int column = 0; column < HEADERS.length; column++) {
            assertEquals(HEADERS[column], sheet.getRow(0).getCell(column).getStringCellValue());
        }
    }

    private Row rowForCode(Sheet sheet, String code) {
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row != null && code.equals(new DataFormatter().formatCellValue(row.getCell(1)))) {
                return row;
            }
        }
        fail("Exported employee not found: " + code);
        return null;
    }

    private void assertText(Row row, int column, String expected) {
        assertEquals(CellType.STRING, row.getCell(column).getCellType());
        assertEquals(expected, row.getCell(column).getStringCellValue());
    }

    private void assertDate(Cell cell, LocalDate expected, String displayed) {
        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertTrue(DateUtil.isCellDateFormatted(cell));
        assertEquals(expected, cell.getLocalDateTimeCellValue().toLocalDate());
        assertEquals(displayed, new DataFormatter().formatCellValue(cell));
    }

    private String uniqueCode() {
        return "EXPORT_" + UUID.randomUUID().toString().replace("-", "");
    }
}
