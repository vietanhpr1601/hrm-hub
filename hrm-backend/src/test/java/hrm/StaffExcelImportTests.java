package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.model.Department;
import hrm.model.Position;
import hrm.model.Staff;
import hrm.repository.StaffRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
@WithMockUser(username = "integration-admin", authorities = "ROLE_ADMIN")
class StaffExcelImportTests {
    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] HEADERS = {"STT", "Mã nhân viên", "Họ và tên", "Ngày sinh", "Giới tính",
            "Số điện thoại", "Email", "Ngày vào làm", "Trạng thái nhân viên", "Phòng ban", "Vị trí",
            "Số CCCD/CMND", "Địa chỉ hiện tại", "Mã số BHXH"};
    private static final String[] REQUIRED_HEADERS = {"Họ và tên", "Email", "Trạng thái nhân viên"};

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;
    @Autowired private StaffRepository staffRepository;
    @Value("${app.admin.password}") private String confirmationPassword;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void templateIsRealXlsxWithMatchingHeadersInstructionsAndActiveCatalogs() throws Exception {
        Department active = department("Phòng nhập Excel " + token());
        Department deleted = department("Phòng đã xóa " + token());
        deleted.setIsDeleted(true);
        Position position = position("Chuyên viên nhập Excel " + token(), active);
        Position removedPosition = position("Vị trí đã xóa " + token(), active);
        removedPosition.setIsDeleted(true);
        entityManager.flush();

        MockHttpServletResponse response = mvc.perform(get("/api/staffs/import-template"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(XLSX_TYPE, response.getContentType());
        ContentDisposition disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("attachment", disposition.getType());
        assertNotNull(disposition.getFilename());
        assertTrue(disposition.getFilename().endsWith(".xlsx"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet staff = workbook.getSheet("Nhan vien");
            assertNotNull(staff);
            assertNotNull(workbook.getSheet("Huong dan"));
            assertNotNull(workbook.getSheet("Danh muc"));
            assertEquals(HEADERS.length, staff.getRow(0).getLastCellNum());
            for (int column = 0; column < HEADERS.length; column++) {
                assertEquals(HEADERS[column], staff.getRow(0).getCell(column).getStringCellValue());
            }
            Set<String> catalog = cellValues(workbook.getSheet("Danh muc"));
            assertTrue(catalog.contains(active.getCode()));
            assertTrue(catalog.contains(active.getName()));
            assertTrue(catalog.contains(position.getCode()));
            assertTrue(catalog.contains(position.getName()));
            assertFalse(catalog.contains(deleted.getCode()));
            assertFalse(catalog.contains(removedPosition.getCode()));
        }
    }

    @Test
    void importsEmployeesWithRelationsExcelAndTextDatesAndPreservesLeadingZeroes() throws Exception {
        Department department = department("Phòng nhân sự " + token());
        Position position = position("Chuyên viên " + token(), department);
        String firstCode = "000" + token();
        String secondCode = "001" + token();
        String firstEmail = email();
        String secondEmail = email();
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            Row first = employeeRow(sheet, 1, firstCode, "Nguyễn Thị Ánh", firstEmail);
            excelDate(first, 3, LocalDate.of(1995, 2, 3));
            text(first, 4, "Nữ");
            text(first, 5, "0901234567");
            text(first, 7, "01/09/2026");
            text(first, 8, "Đang làm việc");
            text(first, 9, department.getCode());
            text(first, 10, position.getCode());
            text(first, 11, "001095000012");
            text(first, 12, "12 Nguyễn Huệ, Thành phố Hồ Chí Minh");
            text(first, 13, "0012345678");
            Row second = employeeRow(sheet, 2, secondCode, "Trần Văn Bình", secondEmail);
            text(second, 3, "1996-04-05");
            text(second, 4, "MALE");
            excelDate(second, 7, LocalDate.of(2026, 9, 2));
            text(second, 9, department.getName());
            text(second, 10, position.getName());
        });

        JsonNode result = importFile(bytes, 200);
        assertEquals(2, result.path("totalRows").asInt());
        assertEquals(2, result.path("importedCount").asInt());
        assertEquals(0, result.path("errors").size());
        flushAndClear();
        assertEquals(before + 2, staffRepository.count());
        Staff first = staffWithCode(firstCode);
        assertEquals("Nguyễn Thị Ánh", first.getDisplayName());
        assertEquals(firstEmail, first.getEmail());
        assertEquals("0901234567", first.getPhoneNumber());
        assertEquals("001095000012", first.getIdNumber());
        assertEquals("0012345678", first.getSocialInsuranceCode());
        assertEquals("FEMALE", first.getGender());
        assertEquals("7", first.getWorkingStatus());
        assertEquals(LocalDate.of(1995, 2, 3), first.getBirthDate());
        assertEquals(LocalDate.of(2026, 9, 1), first.getStartDate());
        assertEquals(department.getId(), first.getDepartment().getId());
        assertEquals(position.getId(), first.getPosition().getId());
        assertEquals("12 Nguyễn Huệ, Thành phố Hồ Chí Minh", first.getCurrentAddress());
        Staff second = staffWithCode(secondCode);
        assertEquals("MALE", second.getGender());
        assertEquals(LocalDate.of(1996, 4, 5), second.getBirthDate());
        assertEquals(LocalDate.of(2026, 9, 2), second.getStartDate());
        assertEquals(position.getId(), second.getPosition().getId());
    }

    @Test
    void downloadedTemplateCanBeFilledAndImportedWithDepartmentInferredFromPosition() throws Exception {
        Department department = department("Phòng trong mẫu " + token());
        Position position = position("Chuyên viên trong mẫu " + token(), department);
        entityManager.flush();
        byte[] template = mvc.perform(get("/api/staffs/import-template"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String code = token();
        byte[] completed;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row row = employeeRow(workbook.getSheet("Nhan vien"), 1, code, "Nhân viên từ mẫu tải về", email());
            text(row, 10, position.getCode());
            workbook.write(output);
            completed = output.toByteArray();
        }
        JsonNode result = importFile(completed, 200);
        assertEquals(1, result.path("importedCount").asInt());
        flushAndClear();
        Staff imported = staffWithCode(code);
        assertEquals(department.getId(), imported.getDepartment().getId());
        assertEquals(position.getId(), imported.getPosition().getId());
    }

    @Test
    void readsExcelDatesUsingTheWorkbooks1904DateSystem() throws Exception {
        String code = token();
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getCTWorkbook().getWorkbookPr().setDate1904(true);
            Sheet sheet = workbook.createSheet("Nhan vien");
            Row header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.length; column++) text(header, column, HEADERS[column]);
            Row row = employeeRow(sheet, 1, code, "Nhân viên dùng ngày Excel", email());
            Cell dateCell = row.createCell(3);
            dateCell.setCellValue(org.apache.poi.ss.usermodel.DateUtil.getExcelDate(LocalDate.of(1997, 6, 15), true));
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy"));
            dateCell.setCellStyle(style);
            workbook.write(output);
            bytes = output.toByteArray();
        }
        importFile(bytes, 200);
        flushAndClear();
        assertEquals(LocalDate.of(1997, 6, 15), staffWithCode(code).getBirthDate());
    }

    @Test
    void acceptsReorderedRequiredHeadersAndGeneratesEmployeeCode() throws Exception {
        String importedEmail = email();
        byte[] bytes = workbook(new String[]{"Email", "Trạng thái nhân viên", "Họ và tên"}, sheet -> {
            Row row = sheet.createRow(1);
            text(row, 0, importedEmail);
            text(row, 1, "1");
            text(row, 2, "Nhân viên tự sinh mã");
        });
        JsonNode result = importFile(bytes, 200);
        assertEquals(1, result.path("importedCount").asInt());
        Staff staff = staffRepository.findAll().stream().filter(item -> importedEmail.equals(item.getEmail()))
                .findFirst().orElseThrow();
        assertTrue(staff.getStaffCode().matches("NV[0-9]{4}_[0-9]+"), staff.getStaffCode());
        assertEquals("1", staff.getWorkingStatus());
        assertNull(staff.getDepartment());
        assertNull(staff.getPosition());
    }

    @Test
    void automaticCodesReserveExplicitCodesAppearingLaterInTheWorkbook() throws Exception {
        String explicitCode = "NV" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "_001";
        String automaticEmail = email();
        byte[] bytes = workbook(HEADERS, sheet -> {
            employeeRow(sheet, 1, "", "Nhân viên mã tự động", automaticEmail);
            employeeRow(sheet, 2, explicitCode, "Nhân viên mã khai báo", email());
            employeeRow(sheet, 3, "", "Nhân viên mã tự động khác", email());
        });
        JsonNode result = importFile(bytes, 200);
        assertEquals(3, result.path("importedCount").asInt());
        flushAndClear();
        assertNotNull(staffWithCode(explicitCode));
        Staff generated = staffRepository.findAll().stream().filter(item -> automaticEmail.equals(item.getEmail()))
                .findFirst().orElseThrow();
        assertNotEquals(explicitCode, generated.getStaffCode());
        assertTrue(generated.getStaffCode().matches("NV[0-9]{4}_[0-9]+"));
    }

    @Test
    void reimportingTheSameFileIsRejectedWithoutDuplicatingEmployees() throws Exception {
        String code = token();
        byte[] bytes = workbook(HEADERS, sheet -> employeeRow(sheet, 1, code, "Nhân viên nhập một lần", email()));
        importFile(bytes, 200);
        long afterFirst = staffRepository.count();
        JsonNode duplicate = importFile(bytes, 400);
        assertValidation(duplicate, 2, "Mã nhân viên");
        assertValidation(duplicate, 2, "Email");
        assertEquals(afterFirst, staffRepository.count());
    }

    @Test
    void rejectsExistingEmailsIgnoringCaseAndExistingDeletedStaffCodes() throws Exception {
        String code = token();
        String occupiedEmail = email();
        Staff active = existingStaff(token(), occupiedEmail);
        Staff deleted = existingStaff(code, email());
        deleted.setIsDeleted(true);
        staffRepository.saveAndFlush(deleted);
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            employeeRow(sheet, 1, token(), "Trùng email", occupiedEmail.toUpperCase());
            employeeRow(sheet, 2, code.toLowerCase(), "Trùng mã đã xóa", email());
        });
        JsonNode result = importFile(bytes, 400);
        assertValidation(result, 2, "Email");
        assertValidation(result, 3, "Mã nhân viên");
        assertEquals(before, staffRepository.count());
        assertEquals(occupiedEmail, staffRepository.findById(active.getId()).orElseThrow().getEmail());
    }

    @Test
    void rejectsCaseInsensitiveDuplicateCodesAndEmailsWithinTheSameFile() throws Exception {
        String code = token();
        String duplicateEmail = email();
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            employeeRow(sheet, 1, code, "Nhân viên thứ nhất", duplicateEmail);
            employeeRow(sheet, 2, code.toLowerCase(), "Nhân viên thứ hai", duplicateEmail.toUpperCase());
        });
        JsonNode result = importFile(bytes, 400);
        assertValidation(result, 3, "Mã nhân viên");
        assertValidation(result, 3, "Email");
        assertEquals(before, staffRepository.count());
    }

    @Test
    void mixedValidAndInvalidRowsReturnExcelRowNumbersAndSaveNothing() throws Exception {
        String validCode = token();
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            employeeRow(sheet, 1, validCode, "Nhân viên hợp lệ", email());
            sheet.createRow(2); // A blank Excel row must not shift the reported row numbers.
            employeeRow(sheet, 3, token(), "", "invalid-email");
        });
        JsonNode result = importFile(bytes, 400);
        assertEquals(2, result.path("totalRows").asInt());
        assertValidation(result, 4, "Họ và tên");
        assertValidation(result, 4, "Email");
        assertEquals(before, staffRepository.count());
        assertTrue(staffRepository.findAll().stream().noneMatch(staff -> validCode.equals(staff.getStaffCode())));
    }

    @Test
    void rejectsMissingRequiredColumnsDuplicateColumnsAndUnknownColumns() throws Exception {
        long before = staffRepository.count();
        for (String[] headers : List.of(
                new String[]{"Họ và tên", "Email"},
                new String[]{"Họ và tên", "Email", "Trạng thái nhân viên", "Email"},
                new String[]{"Họ và tên", "Email", "Trạng thái nhân viên", "Cột không được hỗ trợ"})) {
            byte[] bytes = workbook(headers, sheet -> {
                Row row = sheet.createRow(1);
                text(row, 0, "Nhân viên dữ liệu không hợp lệ");
                text(row, 1, email());
                text(row, 2, "7");
            });
            JsonNode result = importFile(bytes, 400);
            assertEquals(0, result.path("importedCount").asInt());
            assertTrue(result.path("errors").size() > 0, result.toPrettyString());
            assertTrue(errors(result).stream().anyMatch(error -> error.path("row").asInt() == 1), result.toPrettyString());
        }
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsImpossibleDateInvalidGenderStatusAndNumericIdentifiers() throws Exception {
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            Row row = employeeRow(sheet, 1, token(), "Nhân viên sai dữ liệu", email());
            text(row, 3, "31/02/2026");
            text(row, 4, "UNKNOWN");
            row.createCell(5).setCellValue(901234567);
            text(row, 8, "9");
            row.createCell(11).setCellValue(1095000012);
            row.createCell(13).setCellValue(12345678);
        });
        JsonNode result = importFile(bytes, 400);
        for (String field : List.of("Ngày sinh", "Giới tính", "Số điện thoại", "Trạng thái nhân viên", "Số CCCD/CMND", "Mã số BHXH")) {
            assertValidation(result, 2, field);
        }
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsFormulaAndErrorCellsInsteadOfEvaluatingThem() throws Exception {
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            Row row = employeeRow(sheet, 1, token(), "Nhân viên có công thức", email());
            row.getCell(2).setCellFormula("CONCATENATE(\"Nguyễn\",\" Văn An\")");
            row.createCell(12, CellType.ERROR).setCellErrorValue((byte) 7);
        });
        JsonNode result = importFile(bytes, 400);
        assertValidation(result, 2, "Họ và tên");
        assertValidation(result, 2, "Địa chỉ hiện tại");
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsUnknownDeletedAndAmbiguousDepartmentsAndMismatchedPosition() throws Exception {
        String duplicateName = "Phòng trùng tên " + token();
        Department first = department(duplicateName);
        Department second = department(duplicateName);
        Department deleted = department("Phòng ngừng sử dụng " + token());
        deleted.setIsDeleted(true);
        Position secondPosition = position("Vị trí phòng thứ hai " + token(), second);
        entityManager.flush();
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            Row unknown = employeeRow(sheet, 1, token(), "Phòng không tồn tại", email());
            text(unknown, 9, "NOT_FOUND_" + token());
            Row removed = employeeRow(sheet, 2, token(), "Phòng đã xóa", email());
            text(removed, 9, deleted.getCode());
            Row ambiguous = employeeRow(sheet, 3, token(), "Phòng không rõ ràng", email());
            text(ambiguous, 9, duplicateName);
            Row mismatch = employeeRow(sheet, 4, token(), "Vị trí không thuộc phòng", email());
            text(mismatch, 9, first.getCode());
            text(mismatch, 10, secondPosition.getCode());
        });
        JsonNode result = importFile(bytes, 400);
        assertValidation(result, 2, "Phòng ban");
        assertValidation(result, 3, "Phòng ban");
        assertValidation(result, 4, "Phòng ban");
        assertValidation(result, 5, "Vị trí");
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsUnknownDeletedAndAmbiguousPositions() throws Exception {
        Department department = department("Phòng kiểm tra vị trí " + token());
        String duplicateName = "Chuyên viên trùng tên " + token();
        position(duplicateName, department);
        position(duplicateName, department);
        Position deleted = position("Vị trí đã xóa " + token(), department);
        deleted.setIsDeleted(true);
        entityManager.flush();
        long before = staffRepository.count();
        byte[] bytes = workbook(HEADERS, sheet -> {
            String[] values = {"NOT_FOUND_" + token(), deleted.getCode(), duplicateName};
            for (int index = 0; index < values.length; index++) {
                Row row = employeeRow(sheet, index + 1, token(), "Sai vị trí " + index, email());
                text(row, 9, department.getCode());
                text(row, 10, values[index]);
            }
        });
        JsonNode result = importFile(bytes, 400);
        for (int row = 2; row <= 4; row++) assertValidation(result, row, "Vị trí");
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsEmptyCorruptAndNonExcelUploadsWithNoWrites() throws Exception {
        long before = staffRepository.count();
        for (byte[] bytes : List.of(new byte[0], "<html>Not an Excel workbook</html>".getBytes(StandardCharsets.UTF_8),
                "{\"data\":[]}".getBytes(StandardCharsets.UTF_8), new byte[]{'P', 'K', 3, 4, 1, 2, 3})) {
            mvc.perform(multipart("/api/staffs/import-excel").param("password", confirmationPassword)
                            .file(new MockMultipartFile("file", "nhan-vien.xlsx", XLSX_TYPE, bytes)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsMissingFileAndUnsupportedExtensionEvenForAValidWorkbook() throws Exception {
        long before = staffRepository.count();
        mvc.perform(multipart("/api/staffs/import-excel").param("password", confirmationPassword))
                .andExpect(status().isBadRequest());
        byte[] bytes = workbook(HEADERS, sheet -> employeeRow(sheet, 1, token(), "Sai đuôi file", email()));
        mvc.perform(multipart("/api/staffs/import-excel").param("password", confirmationPassword)
                        .file(new MockMultipartFile("file", "nhan-vien.xls", XLSX_TYPE, bytes)))
                .andExpect(status().isBadRequest());
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsOversizedUploadsAndMoreThanOneThousandEmployees() throws Exception {
        long before = staffRepository.count();
        mvc.perform(multipart("/api/staffs/import-excel").param("password", confirmationPassword)
                        .file(new MockMultipartFile("file", "qua-lon.xlsx", XLSX_TYPE, new byte[1024 * 1024 + 1])))
                .andExpect(status().isBadRequest());
        byte[] bytes = workbook(REQUIRED_HEADERS, sheet -> {
            for (int index = 1; index <= 1001; index++) {
                Row row = sheet.createRow(index);
                text(row, 0, "Nhân viên " + index);
                text(row, 1, "row" + index + "@example.test");
                text(row, 2, "7");
            }
        });
        MockHttpServletResponse response = mvc.perform(multipart("/api/staffs/import-excel").param("password", confirmationPassword)
                        .file(new MockMultipartFile("file", "nhan-vien.xlsx", XLSX_TYPE, bytes)))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        JsonNode result = mapper.readTree(response.getContentAsByteArray());
        assertTrue(result.path("message").asText().contains("1.000"), result.toPrettyString());
        assertEquals(before, staffRepository.count());
    }

    @Test
    void rejectsHeaderOnlyWorkbookWithoutSavingStaff() throws Exception {
        long before = staffRepository.count();
        JsonNode result = importFile(workbook(HEADERS, sheet -> {}), 400);
        assertEquals(0, result.path("importedCount").asInt());
        assertTrue(result.path("errors").size() > 0, result.toPrettyString());
        assertEquals(before, staffRepository.count());
    }

    @Test
    void retainsAValidUniqueWorkbookForIsolatedBrowserVerification() throws Exception {
        String code = "IMPORT_UI_" + token();
        byte[] bytes = workbook(HEADERS, sheet -> {
            Row row = employeeRow(sheet, 1, code, "Nguyễn Thị Minh Anh", email());
            text(row, 3, "15/04/1998");
            text(row, 4, "Nữ");
            text(row, 5, "0901234567");
            text(row, 7, "01/10/2026");
            text(row, 11, "001098000123");
            text(row, 12, "Thành phố Hồ Chí Minh");
            text(row, 13, "0012345678");
        });
        JsonNode result = importFile(bytes, 200);
        assertEquals(1, result.path("importedCount").asInt());
        assertNotNull(staffWithCode(code));
        Path fixture = Path.of("target", "staff-import-verification", "new-staff.xlsx");
        Files.createDirectories(fixture.getParent());
        Files.write(fixture, bytes);
    }

    @Test
    @WithAnonymousUser
    void anonymousClientCannotDownloadTheTemplateOrImportStaff() throws Exception {
        mvc.perform(get("/api/staffs/import-template")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/staffs/import-excel").param("password", confirmationPassword).file(new MockMultipartFile("file", "nhan-vien.xlsx", XLSX_TYPE,
                workbook(HEADERS, sheet -> employeeRow(sheet, 1, token(), "Không được phép", email())))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode importFile(byte[] bytes, int expectedStatus) throws Exception {
        MockHttpServletResponse response = mvc.perform(multipart("/api/staffs/import-excel").param("password", confirmationPassword)
                        .file(new MockMultipartFile("file", "nhan-vien.xlsx", XLSX_TYPE, bytes)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse();
        return mapper.readTree(response.getContentAsByteArray()).path("data");
    }

    private void assertValidation(JsonNode result, int row, String field) {
        assertEquals(0, result.path("importedCount").asInt(), result.toPrettyString());
        assertTrue(errors(result).stream().anyMatch(error -> error.path("row").asInt() == row
                        && field.equals(error.path("field").asText()) && !error.path("message").asText().isBlank()),
                "Expected a validation error at Excel row " + row + ", field " + field + ": " + result.toPrettyString());
    }

    private List<JsonNode> errors(JsonNode result) {
        List<JsonNode> errors = new ArrayList<>();
        result.path("errors").forEach(errors::add);
        return errors;
    }

    private byte[] workbook(String[] headers, Consumer<Sheet> writer) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Nhan vien");
            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) text(header, column, headers[column]);
            writer.accept(sheet);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private Row employeeRow(Sheet sheet, int index, String code, String name, String email) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(index);
        text(row, 1, code);
        text(row, 2, name);
        text(row, 6, email);
        text(row, 8, "7");
        return row;
    }

    private void text(Row row, int column, String value) {
        row.createCell(column, CellType.STRING).setCellValue(value);
    }

    private void excelDate(Row row, int column, LocalDate date) {
        Cell cell = row.createCell(column);
        cell.setCellValue(date);
        CellStyle style = row.getSheet().getWorkbook().createCellStyle();
        style.setDataFormat(row.getSheet().getWorkbook().createDataFormat().getFormat("dd/MM/yyyy"));
        cell.setCellStyle(style);
    }

    private Set<String> cellValues(Sheet sheet) {
        Set<String> values = new HashSet<>();
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) for (Cell cell : row) values.add(formatter.formatCellValue(cell));
        return values;
    }

    private Department department(String name) {
        Department department = new Department();
        department.setCode(token());
        department.setName(name);
        entityManager.persist(department);
        return department;
    }

    private Position position(String name, Department department) {
        Position position = new Position();
        position.setCode(token());
        position.setName(name);
        position.setDepartment(department);
        entityManager.persist(position);
        return position;
    }

    private Staff existingStaff(String code, String email) {
        Staff staff = new Staff();
        staff.setStaffCode(code);
        staff.setDisplayName("Nhân viên đã tồn tại");
        staff.setEmail(email);
        staff.setWorkingStatus("7");
        return staffRepository.save(staff);
    }

    private Staff staffWithCode(String code) {
        return staffRepository.findAll().stream().filter(staff -> code.equals(staff.getStaffCode()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing imported employee: " + code));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String token() {
        return "IMPORT_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String email() {
        return UUID.randomUUID().toString().replace("-", "") + "@example.test";
    }
}
