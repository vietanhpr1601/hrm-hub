package hrm;

import hrm.dto.request.LeaveRequestDto;
import hrm.enums.CheckInOutType;
import hrm.enums.LeaveType;
import hrm.enums.TimesheetStatus;
import hrm.model.CheckInOutRecord;
import hrm.model.ShiftWork;
import hrm.model.Staff;
import hrm.model.Timesheet;
import hrm.repository.CheckInOutRecordRepository;
import hrm.repository.ShiftWorkRepository;
import hrm.repository.StaffRepository;
import hrm.repository.TimesheetRepository;
import hrm.service.LeaveRequestService;
import hrm.service.TimesheetService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@HrmIntegrationTest
class LeaveAttendanceReapprovalTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    @Autowired StaffRepository staffs;
    @Autowired ShiftWorkRepository shifts;
    @Autowired CheckInOutRecordRepository logs;
    @Autowired TimesheetRepository sheets;
    @Autowired LeaveRequestService leaves;
    @Autowired TimesheetService attendance;
    @Autowired EntityManager entityManager;

    @Test
    void rejectingApprovedLeaveRequiresSeparateApprovalOfRestoredActualWork() {
        Staff staff = staff();
        work(staff);
        LeaveRequestDto leave = approvedLeave(staff);
        assertEquals(TimesheetStatus.APPROVED, sheet(staff).getStatus());

        leaves.reject(leave.getId(), "Đi làm thay vì nghỉ");
        flush();
        Timesheet restored = sheet(staff);
        assertEquals(1.0, restored.getTotalWorkRatio());
        assertEquals(8.0, restored.getStandardHours());
        assertEquals(TimesheetStatus.SUBMITTED, restored.getStatus());
        assertTrue(approvedForPayroll(staff).isEmpty());
        assertFalse(restored.getNote().contains("Nghỉ cả ngày"));

        attendance.approve(restored.getId(), TimesheetStatus.APPROVED, "Đã đối chiếu lượt đi làm");
        flush();
        assertEquals(1, approvedForPayroll(staff).size());
    }

    @Test
    void deletingApprovedLeaveDoesNotTransferApprovalToRawAttendance() {
        Staff staff = staff();
        work(staff);
        LeaveRequestDto leave = approvedLeave(staff);
        leaves.delete(leave.getId());
        flush();
        assertEquals(TimesheetStatus.SUBMITTED, sheet(staff).getStatus());
        assertEquals(1.0, sheet(staff).getTotalWorkRatio());
        assertTrue(approvedForPayroll(staff).isEmpty());
        assertEquals(0.0, leaves.getLeaveBalance(staff.getId(), DAY.getYear()).getUsedDays());
    }

    @Test
    void cancelledLeaveWithoutAttendanceRemovesPaidCredit() {
        Staff staff = staff();
        LeaveRequestDto leave = approvedLeave(staff);
        leaves.delete(leave.getId());
        flush();
        assertEquals(0.0, sheet(staff).getTotalWorkRatio());
        assertEquals(0.0, sheet(staff).getStandardHours());
        assertEquals(TimesheetStatus.SUBMITTED, sheet(staff).getStatus());
        assertTrue(approvedForPayroll(staff).isEmpty());
    }

    @Test
    void deletingPendingLeavePreservesSeparatelyApprovedAttendance() {
        Staff staff = staff();
        work(staff);
        attendance.calculateTimesheet(staff.getId(), DAY);
        flush();
        attendance.approve(sheet(staff).getId(), TimesheetStatus.APPROVED, "Đã đối chiếu");
        LeaveRequestDto leave = leaves.create(request(staff));
        leaves.delete(leave.getId());
        flush();
        assertEquals(TimesheetStatus.APPROVED, sheet(staff).getStatus());
        assertEquals(1, approvedForPayroll(staff).size());
    }

    private Staff staff() {
        Staff staff = new Staff();
        staff.setStaffCode("REAPPROVAL_" + UUID.randomUUID());
        staff.setDisplayName("Nhân viên kiểm thử duyệt lại");
        staff.setAnnualLeave(12.0);
        return staffs.saveAndFlush(staff);
    }

    private void work(Staff staff) {
        ShiftWork shift = shifts.findByCode("CA_CA_NGAY").orElseThrow();
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 30));
        for (CheckInOutType type : CheckInOutType.values()) {
            CheckInOutRecord log = new CheckInOutRecord();
            log.setStaff(staff);
            log.setShift(shift);
            log.setRecordType(type);
            log.setRecordTime(DAY.atTime(type == CheckInOutType.CHECK_IN ? LocalTime.of(8, 0) : LocalTime.of(17, 30)));
            logs.save(log);
        }
        entityManager.flush();
    }

    private LeaveRequestDto request(Staff staff) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setRequestStaffId(staff.getId());
        dto.setLeaveType(LeaveType.ANNUAL);
        dto.setFromDate(DAY);
        dto.setToDate(DAY);
        dto.setRequestReason("Kiểm thử thu hồi phép");
        return dto;
    }

    private LeaveRequestDto approvedLeave(Staff staff) {
        LeaveRequestDto leave = leaves.create(request(staff));
        leaves.approve(leave.getId(), null);
        flush();
        return leave;
    }

    private Timesheet sheet(Staff staff) {
        return sheets.findByStaffIdAndWorkingDate(staff.getId(), DAY).orElseThrow();
    }

    private java.util.List<Timesheet> approvedForPayroll(Staff staff) {
        return sheets.findActiveTimesheetsByStaffIdAndWorkingDateBetweenAndStatus(staff.getId(), DAY, DAY, TimesheetStatus.APPROVED);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
