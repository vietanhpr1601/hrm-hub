package hrm.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Permission {
    NOTIFICATION_VIEW("Thông báo", "Xem thông báo của mình"),
    DASHBOARD_VIEW("Tổng quan", "Xem thống kê"),
    STAFF_VIEW("Nhân viên", "Xem hồ sơ nhân viên"),
    STAFF_CREATE("Nhân viên", "Thêm nhân viên"),
    STAFF_UPDATE("Nhân viên", "Sửa nhân viên"),
    STAFF_DELETE("Nhân viên", "Xóa nhân viên"),
    STAFF_IMPORT("Nhân viên", "Nhập Excel nhân viên mới"),
    STAFF_EXPORT("Nhân viên", "Xuất Excel nhân viên"),
    DEPARTMENT_VIEW("Tổ chức", "Xem phòng ban"),
    DEPARTMENT_MANAGE("Tổ chức", "Thêm, sửa, xóa phòng ban"),
    POSITION_VIEW("Tổ chức", "Xem vị trí"),
    POSITION_MANAGE("Tổ chức", "Thêm, sửa, xóa vị trí"),
    RECRUITMENT_VIEW("Tuyển dụng", "Xem tin tuyển dụng"),
    RECRUITMENT_MANAGE("Tuyển dụng", "Quản lý tin tuyển dụng"),
    CANDIDATE_VIEW("Tuyển dụng", "Xem ứng viên"),
    CANDIDATE_MANAGE("Tuyển dụng", "Quản lý ứng viên, chuyển thành nhân viên"),
    ATTENDANCE_VIEW("Chấm công", "Xem chấm công nhân viên"),
    ATTENDANCE_MANAGE("Chấm công", "Cập nhật chấm công nhân viên"),
    ATTENDANCE_APPROVE("Chấm công", "Duyệt, từ chối chấm công"),
    ATTENDANCE_SELF("Chấm công", "Chấm công và xem công của mình"),
    LEAVE_VIEW("Nghỉ phép", "Xem đơn và số dư phép nhân viên"),
    LEAVE_MANAGE("Nghỉ phép", "Quản lý đơn nghỉ của nhân viên"),
    LEAVE_APPROVE("Nghỉ phép", "Duyệt, từ chối đơn nghỉ"),
    LEAVE_SELF("Nghỉ phép", "Tạo, xem, sửa đơn nghỉ của mình"),
    PAYROLL_VIEW("Tiền lương", "Xem bảng lương nhân viên"),
    PAYROLL_MANAGE("Tiền lương", "Tạo, tính lại, xóa bảng lương nháp"),
    PAYROLL_CONFIRM("Tiền lương", "Chốt bảng lương"),
    PAYROLL_PAY("Tiền lương", "Cập nhật thanh toán phiếu lương"),
    PAYROLL_SELF("Tiền lương", "Xem phiếu lương của mình"),
    SALARY_CONFIG_VIEW("Tiền lương", "Xem khoản lương và cấu hình lương"),
    SALARY_CONFIG_MANAGE("Tiền lương", "Quản lý khoản lương và cấu hình lương"),
    PERIOD_VIEW("Tiền lương", "Xem kỳ lương"),
    PERIOD_MANAGE("Tiền lương", "Quản lý kỳ lương"),
    SHIFT_VIEW("Chấm công", "Xem ca làm việc"),
    SHIFT_MANAGE("Chấm công", "Quản lý ca làm việc"),
    PROJECT_VIEW("Dự án & công việc", "Xem mọi dự án và công việc (kể cả dự án không tham gia)"),
    PROJECT_MANAGE("Dự án & công việc", "Tạo, sửa, xóa mọi dự án và quản lý công việc trong mọi dự án"),
    AI_CHAT("Trợ lý", "Sử dụng trợ lý AI theo quyền dữ liệu được cấp");

    private final String module;
    private final String label;
    Permission(String module, String label) { this.module = module; this.label = label; }
    public String getModule() { return module; }
    public String getLabel() { return label; }
    public static Set<String> codes() { return Arrays.stream(values()).map(Enum::name).collect(Collectors.toSet()); }
}
