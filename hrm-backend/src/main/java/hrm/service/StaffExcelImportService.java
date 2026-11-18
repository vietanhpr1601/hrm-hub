package hrm.service;

import hrm.dto.request.StaffDto;
import hrm.dto.response.StaffImportResult;
import hrm.dto.response.StaffImportResult.RowError;
import hrm.model.BaseModel;
import hrm.model.Department;
import hrm.model.Position;
import hrm.model.Staff;
import hrm.repository.DepartmentRepository;
import hrm.repository.PositionRepository;
import hrm.repository.StaffRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class StaffExcelImportService {
    private static final int MAX_ROWS = 1000;
    private static final long MAX_BYTES = 1024 * 1024;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String[] HEADERS = StaffExcelExportService.HEADERS;
    private static final List<String> GENDERS = List.of("Nam", "Nữ", "Khác");
    private static final List<String> STATUSES = List.of("Chờ nhận việc", "Tạm dừng công việc", "Nghỉ chế độ",
            "Không nhận việc", "Đã nghỉ việc", "Nghỉ việc đi làm lại", "Đang làm việc", "Nghỉ không lương");
    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final StaffService staffService;

    public StaffExcelImportService(StaffRepository staffRepository, DepartmentRepository departmentRepository,
                                   PositionRepository positionRepository, StaffService staffService) {
        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.staffService = staffService;
    }

    @Transactional
    public StaffImportResult importFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn file Excel có dữ liệu.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file Excel .xlsx. Hãy tải và sử dụng mẫu nhập nhân viên mới.");
        }
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("File vượt quá dung lượng 1 MB.");
        try (XSSFWorkbook workbook = openWorkbook(file)) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("File không có trang dữ liệu.");
            Sheet sheet = workbook.getSheet("Nhan vien");
            if (sheet == null) sheet = workbook.getSheetAt(0);
            List<RowError> errors = new ArrayList<>();
            Map<Integer, Integer> columns = readHeaders(sheet.getRow(0), errors);
            if (!errors.isEmpty()) return new StaffImportResult(0, 0, errors);

            List<Staff> existing = staffRepository.findAll();
            Set<String> codes = new HashSet<>();
            Set<String> emails = new HashSet<>();
            for (Staff staff : existing) {
                if (staff.getStaffCode() != null) codes.add(key(staff.getStaffCode()));
                if (active(staff) && staff.getEmail() != null && !staff.getEmail().isBlank()) emails.add(key(staff.getEmail()));
            }
            List<Department> departments = departmentRepository.findAll().stream().filter(StaffExcelImportService::active).toList();
            List<Position> positions = positionRepository.findAll().stream().filter(StaffExcelImportService::active).toList();
            List<StaffDto> toCreate = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            int totalRows = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || blank(row, columns, formatter)) continue;
                totalRows++;
                if (totalRows > MAX_ROWS) throw new IllegalArgumentException("Mỗi lần chỉ nhập tối đa 1.000 nhân viên.");
                int rowNumber = row.getRowNum() + 1;
                String[] values = new String[HEADERS.length];
                Arrays.fill(values, "");
                for (Map.Entry<Integer, Integer> column : columns.entrySet()) {
                    int field = column.getKey();
                    if (field == 0) continue; // STT is display-only.
                    Cell cell = row.getCell(column.getValue());
                    if (cell == null) continue;
                    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                        error(errors, rowNumber, field, "Ô phải chứa giá trị trực tiếp, không dùng công thức hoặc ô lỗi.");
                        continue;
                    }
                    values[field] = formatter.formatCellValue(cell).trim();
                    if (field != 3 && field != 7 && values[field].length() > 255) {
                        error(errors, rowNumber, field, "Giá trị không được vượt quá 255 ký tự.");
                    }
                    if (Set.of(1, 5, 11, 13).contains(field) && cell.getCellType() == CellType.NUMERIC) {
                        error(errors, rowNumber, field, "Nhập ô dạng Văn bản (Text) để giữ nguyên mã và số 0 đầu.");
                    }
                }
                StaffDto dto = new StaffDto();
                dto.setStaffCode(values[1].isEmpty() ? null : values[1]);
                dto.setDisplayName(values[2]);
                if (values[2].isBlank()) error(errors, rowNumber, 2, "Họ và tên là bắt buộc.");
                if (!values[1].isBlank() && !codes.add(key(values[1]))) error(errors, rowNumber, 1, "Mã nhân viên đã tồn tại hoặc bị trùng trong file.");
                dto.setEmail(values[6]);
                if (values[6].isBlank()) error(errors, rowNumber, 6, "Email là bắt buộc.");
                else if (!EMAIL.matcher(values[6]).matches()) error(errors, rowNumber, 6, "Email không đúng định dạng.");
                else if (!emails.add(key(values[6]))) error(errors, rowNumber, 6, "Email đã tồn tại hoặc bị trùng trong file.");
                dto.setBirthDate(readDate(row, columns.get(3), values[3], workbook.isDate1904(), rowNumber, 3, errors));
                dto.setStartDate(readDate(row, columns.get(7), values[7], workbook.isDate1904(), rowNumber, 7, errors));
                dto.setGender(gender(values[4], rowNumber, errors));
                dto.setWorkingStatus(status(values[8], rowNumber, errors));
                dto.setPhoneNumber(values[5]);
                dto.setIdNumber(values[11]);
                dto.setCurrentAddress(values[12]);
                dto.setSocialInsuranceCode(values[13]);
                Department department = resolve(values[9], departments, Department::getCode, Department::getName, rowNumber, 9, errors);
                Position position = resolve(values[10], positions, Position::getCode, Position::getName, rowNumber, 10, errors);
                if (position != null && position.getDepartment() != null) {
                    Department positionDepartment = position.getDepartment();
                    if (!active(positionDepartment)) error(errors, rowNumber, 10, "Vị trí thuộc phòng ban đã ngừng sử dụng.");
                    else if (department != null && !department.getId().equals(positionDepartment.getId())) {
                        error(errors, rowNumber, 10, "Vị trí không thuộc phòng ban đã chọn.");
                    } else if (department == null && values[9].isBlank()) department = positionDepartment;
                }
                dto.setDepartmentId(department == null ? null : department.getId());
                dto.setPositionId(position == null ? null : position.getId());
                toCreate.add(dto);
            }
            if (totalRows == 0) errors.add(new RowError(2, "Dữ liệu", "File chưa có dữ liệu nhân viên."));
            if (!errors.isEmpty()) return new StaffImportResult(totalRows, 0, errors);

            // Reserve every explicit code before generating codes for blank cells.
            String prefix = "NV" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "_";
            long next = 1;
            for (String code : codes) {
                if (code.matches("nv[0-9]{4}_[0-9]{1,9}")) {
                    next = Math.max(next, Long.parseLong(code.substring(code.indexOf('_') + 1)) + 1);
                }
            }
            for (StaffDto dto : toCreate) {
                if (dto.getStaffCode() == null) {
                    String generated;
                    do { generated = prefix + String.format(Locale.ROOT, "%03d", next++); } while (!codes.add(key(generated)));
                    dto.setStaffCode(generated);
                }
                staffService.saveStaff(dto);
            }
            staffRepository.flush();
            return new StaffImportResult(totalRows, toCreate.size(), List.of());
        }
    }

    private XSSFWorkbook openWorkbook(MultipartFile file) {
        try (var input = file.getInputStream()) {
            return new XSSFWorkbook(input);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Không đọc được file .xlsx. File phải là Excel hợp lệ và không đặt mật khẩu.");
        }
    }

    private static Map<Integer, Integer> readHeaders(Row header, List<RowError> errors) {
        Map<Integer, Integer> columns = new LinkedHashMap<>();
        if (header != null) for (Cell cell : header) {
            if (cell.getCellType() == CellType.BLANK) continue;
            String title = cell.getCellType() == CellType.STRING ? cell.getStringCellValue().trim() : "";
            if (title.isBlank() && cell.getCellType() == CellType.STRING) continue;
            int field = -1;
            for (int i = 0; i < HEADERS.length; i++) if (key(HEADERS[i]).equals(key(title))) field = i;
            if (field < 0) errors.add(new RowError(1, "Cột " + (cell.getColumnIndex() + 1), "Tiêu đề không hợp lệ. Hãy dùng mẫu nhập nhân viên mới."));
            else if (columns.putIfAbsent(field, cell.getColumnIndex()) != null) error(errors, 1, field, "Tiêu đề cột bị trùng.");
        }
        for (int required : new int[]{2, 6, 8}) if (!columns.containsKey(required)) error(errors, 1, required, "Thiếu cột bắt buộc.");
        return columns;
    }

    private static boolean blank(Row row, Map<Integer, Integer> columns, DataFormatter formatter) {
        for (Map.Entry<Integer, Integer> column : columns.entrySet()) {
            if (column.getKey() == 0) continue;
            Cell cell = row.getCell(column.getValue());
            if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR
                    || !formatter.formatCellValue(cell).isBlank())) return false;
        }
        return true;
    }

    private static LocalDate readDate(Row row, Integer column, String value, boolean date1904, int number, int field, List<RowError> errors) {
        if (column == null || value.isBlank()) return null;
        Cell cell = row.getCell(column);
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                    && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                return DateUtil.getLocalDateTime(cell.getNumericCellValue(), date1904).toLocalDate();
            }
            if (cell.getCellType() != CellType.STRING) throw new IllegalArgumentException();
            return value.contains("/") ? LocalDate.parse(value, DATE_FORMAT) : LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            error(errors, number, field, "Ngày không hợp lệ; nhập dd/MM/yyyy hoặc yyyy-MM-dd.");
            return null;
        }
    }

    private static String gender(String value, int row, List<RowError> errors) {
        if (value.isBlank()) return null;
        String[] codes = {"MALE", "FEMALE", "OTHER"};
        for (int i = 0; i < codes.length; i++) if (key(value).equals(key(codes[i])) || key(value).equals(key(GENDERS.get(i)))) return codes[i];
        error(errors, row, 4, "Giới tính phải là Nam, Nữ hoặc Khác.");
        return null;
    }

    private static String status(String value, int row, List<RowError> errors) {
        for (int i = 0; i < STATUSES.size(); i++) if (value.equals(String.valueOf(i + 1)) || key(value).equals(key(STATUSES.get(i)))) return String.valueOf(i + 1);
        error(errors, row, 8, value.isBlank() ? "Trạng thái nhân viên là bắt buộc." : "Trạng thái không hợp lệ; dùng mã 1–8 hoặc tên trong mẫu.");
        return null;
    }

    private static <T> T resolve(String value, List<T> records, Function<T, String> code, Function<T, String> name,
                                 int row, int field, List<RowError> errors) {
        if (value.isBlank()) return null;
        List<T> matched = records.stream().filter(item -> key(value).equals(key(code.apply(item)))).toList();
        if (matched.isEmpty()) matched = records.stream().filter(item -> key(value).equals(key(name.apply(item)))).toList();
        if (matched.size() == 1) return matched.get(0);
        error(errors, row, field, matched.isEmpty() ? "Không tìm thấy mã hoặc tên đang sử dụng trong danh mục."
                : "Tên bị trùng trong danh mục; vui lòng nhập mã để xác định chính xác.");
        return null;
    }

    private static boolean active(BaseModel entity) { return !Boolean.TRUE.equals(entity.getIsDeleted()); }
    private static String key(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static void error(List<RowError> errors, int row, int field, String message) { errors.add(new RowError(row, HEADERS[field], message)); }

    @Transactional(readOnly = true)
    public byte[] template() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Nhan vien");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setWrapText(true);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy"));
            Row header = sheet.createRow(0);
            header.setHeightInPoints(32);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, (i == 0 ? 7 : i == 2 || i == 6 || i == 12 ? 32 : 24) * 256);
                sheet.setDefaultColumnStyle(i, i == 3 || i == 7 ? dateStyle : textStyle);
            }
            sheet.createFreezePane(0, 1);
            DataValidationHelper helper = sheet.getDataValidationHelper();
            sheet.addValidationData(helper.createValidation(helper.createExplicitListConstraint(GENDERS.toArray(String[]::new)),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, MAX_ROWS, 4, 4)));
            sheet.addValidationData(helper.createValidation(helper.createExplicitListConstraint(STATUSES.toArray(String[]::new)),
                    new org.apache.poi.ss.util.CellRangeAddressList(1, MAX_ROWS, 8, 8)));

            Sheet guide = workbook.createSheet("Huong dan");
            guide.setColumnWidth(0, 115 * 256);
            String[] instructions = {
                    "NHẬP NHÂN VIÊN MỚI",
                    "Điền dữ liệu từ dòng 2 tại trang Nhan vien; giữ nguyên tên các cột.",
                    "Bắt buộc: Họ và tên, Email, Trạng thái nhân viên. Các trường khác có thể để trống.",
                    "Mã nhân viên: để trống để tự tạo; nếu tự nhập thì không được trùng mã đã có hoặc mã trong file.",
                    "Email phải đúng định dạng và chưa được nhân viên khác sử dụng. Chức năng này chỉ thêm mới.",
                    "Ngày sinh, ngày vào làm: ô ngày Excel hoặc chuỗi dd/MM/yyyy, yyyy-MM-dd.",
                    "Mã nhân viên, số điện thoại, CCCD/CMND, BHXH: nhập dạng Text để giữ số 0 đầu.",
                    "Phòng ban, vị trí: dùng mã tại trang Danh muc hoặc tên chính xác; để trống nếu chưa phân công.",
                    "Nếu vị trí thuộc một phòng ban, phòng ban nhập phải khớp; bỏ trống thì hệ thống lấy phòng ban của vị trí.",
                    "Giới tính: Nam, Nữ, Khác. Trạng thái: chọn danh sách hoặc dùng mã 1–8 tại trang Danh muc.",
                    "Không dùng công thức. File tối đa 1 MB, mỗi lần tối đa 1.000 nhân viên. Dòng trống được bỏ qua.",
                    "Nếu có bất kỳ dòng lỗi, toàn bộ đợt nhập chưa được lưu. Sửa các dòng được báo rồi nhập lại.",
                    "Không tự tạo tài khoản đăng nhập hoặc cấu hình lương khi nhập nhân viên."
            };
            CellStyle guideStyle = workbook.createCellStyle();
            guideStyle.setWrapText(true);
            for (int i = 0; i < instructions.length; i++) {
                Row row = guide.createRow(i);
                row.setHeightInPoints(32);
                Cell cell = row.createCell(0);
                cell.setCellValue(instructions[i]);
                cell.setCellStyle(i == 0 ? headerStyle : guideStyle);
            }

            Sheet catalog = workbook.createSheet("Danh muc");
            String[] catalogHeaders = {"Mã phòng ban", "Tên phòng ban", "Mã vị trí", "Tên vị trí", "Phòng ban của vị trí", "Mã trạng thái", "Tên trạng thái"};
            Row catalogHeader = catalog.createRow(0);
            for (int i = 0; i < catalogHeaders.length; i++) {
                Cell cell = catalogHeader.createCell(i);
                cell.setCellValue(catalogHeaders[i]);
                cell.setCellStyle(headerStyle);
                catalog.setColumnWidth(i, 30 * 256);
            }
            List<Department> departments = departmentRepository.findAll().stream().filter(StaffExcelImportService::active).toList();
            List<Position> positions = positionRepository.findAll().stream().filter(StaffExcelImportService::active)
                    .filter(position -> position.getDepartment() == null || active(position.getDepartment())).toList();
            for (int i = 0; i < Math.max(STATUSES.size(), Math.max(departments.size(), positions.size())); i++) {
                Row row = catalog.createRow(i + 1);
                if (i < departments.size()) {
                    row.createCell(0).setCellValue(departments.get(i).getCode());
                    row.createCell(1).setCellValue(departments.get(i).getName());
                }
                if (i < positions.size()) {
                    Position position = positions.get(i);
                    row.createCell(2).setCellValue(position.getCode());
                    row.createCell(3).setCellValue(position.getName());
                    row.createCell(4).setCellValue(position.getDepartment() == null ? "" : position.getDepartment().getCode());
                }
                if (i < STATUSES.size()) {
                    row.createCell(5).setCellValue(String.valueOf(i + 1));
                    row.createCell(6).setCellValue(STATUSES.get(i));
                }
            }
            catalog.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
