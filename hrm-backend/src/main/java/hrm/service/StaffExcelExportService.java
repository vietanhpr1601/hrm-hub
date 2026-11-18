package hrm.service;

import hrm.dto.request.StaffDto;
import hrm.dto.search.SearchDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class StaffExcelExportService {
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String[] HEADERS = {
            "STT", "Mã nhân viên", "Họ và tên", "Ngày sinh", "Giới tính", "Số điện thoại", "Email",
            "Ngày vào làm", "Trạng thái nhân viên", "Phòng ban", "Vị trí", "Số CCCD/CMND",
            "Địa chỉ hiện tại", "Mã số BHXH"
    };
    private static final int[] WIDTHS = {7, 18, 30, 16, 12, 20, 32, 16, 24, 28, 26, 22, 45, 22};
    private static final Map<String, String> GENDERS = Map.of("MALE", "Nam", "FEMALE", "Nữ", "OTHER", "Khác");
    private static final Map<String, String> WORKING_STATUSES = Map.of(
            "1", "Chờ nhận việc", "2", "Tạm dừng công việc", "3", "Nghỉ chế độ", "4", "Không nhận việc",
            "5", "Đã nghỉ việc", "6", "Nghỉ việc đi làm lại", "7", "Đang làm việc", "8", "Nghỉ không lương"
    );
    private final StaffService staffService;

    public StaffExcelExportService(StaffService staffService) {
        this.staffService = staffService;
    }

    @Transactional(readOnly = true)
    public byte[] export(SearchDto filters) throws IOException {
        // Keep the same filtering rules as the staff list, but include every matching row.
        SearchDto exportFilters = new SearchDto();
        if (filters != null) {
            BeanUtils.copyProperties(filters, exportFilters);
        }
        exportFilters.setPageIndex(1);
        exportFilters.setPageSize(Integer.MAX_VALUE);
        List<StaffDto> staffs = staffService.getAllStaffs(exportFilters).getContent();

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Nhan vien");
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy"));
            Row header = sheet.createRow(0);
            header.setHeightInPoints(32);
            for (int column = 0; column < HEADERS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS[column]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column, WIDTHS[column] * 256);
            }

            for (int index = 0; index < staffs.size(); index++) {
                StaffDto staff = staffs.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(index + 1);
                text(row, 1, staff.getStaffCode());
                text(row, 2, staff.getDisplayName());
                date(row, 3, staff.getBirthDate(), dateStyle);
                text(row, 4, label(GENDERS, staff.getGender()));
                text(row, 5, staff.getPhoneNumber());
                text(row, 6, staff.getEmail());
                date(row, 7, staff.getStartDate(), dateStyle);
                text(row, 8, label(WORKING_STATUSES, staff.getWorkingStatus()));
                text(row, 9, staff.getDepartmentName());
                text(row, 10, staff.getPositionName());
                text(row, 11, staff.getIdNumber());
                text(row, 12, staff.getCurrentAddress());
                text(row, 13, staff.getSocialInsuranceCode());
            }

            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, staffs.size(), 0, HEADERS.length - 1));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String label(Map<String, String> labels, String value) {
        return value == null ? "" : labels.getOrDefault(value, value);
    }

    private static void text(Row row, int column, String value) {
        // Explicit text cells preserve leading zeroes and do not execute formula-like user input.
        row.createCell(column, CellType.STRING).setCellValue(value == null ? "" : value);
    }

    private static void date(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }
}
