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

/** Ngày công đã duyệt được tính là công thực tế khi tính lương. */
@HrmIntegrationTest
class ApprovedAttendanceCreditTests {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 4);
    @Autowired StaffRepository staffs;
    @Autowired ShiftWorkRepository shifts;
    @Autowired CheckInOutRecordRepository logs;
    @Autowired TimesheetRepository sheets;
    @Autowired TimesheetService attendance;
    @Autowired LeaveRequestService leaves;
    @Autowired EntityManager entityManager;

    @Test
    void approvingPunchOutsideShiftHoursCountsFullDay() {
        Staff staff = staff();
        punch(staff, CheckInOutType.CHECK_IN, LocalTime.of(20, 47));
        punch(staff, CheckInOutType.CHECK_OUT, LocalTime.of(20, 48));
        attendance.calculateTimesheet(staff.getId(), DAY);
        flush();
        assertEquals(0.0, sheet(staff).getTotalWorkRatio());
        assertEquals(TimesheetStatus.SUBMITTED, sheet(staff).getStatus());

        attendance.approve(sheet(staff).getId(), TimesheetStatus.APPROVED, null);
        flush();
        assertEquals(1.0, sheet(staff).getTotalWorkRatio());
        assertEquals(1.0, sheet(staff).getDetails().get(0).getWorkRatio());
    }

    @Test
    void approvedDayWithMissingCheckOutKeepsCreditAfterLaterPunch() {
        Staff staff = staff();
        punch(staff, CheckInOutType.CHECK_IN, LocalTime.of(16, 57));
        attendance.calculateTimesheet(staff.getId(), DAY);
        flush();
        attendance.approve(sheet(staff).getId(), TimesheetStatus.APPROVED, null);
        flush();
        assertEquals(1.0, sheet(staff).getTotalWorkRatio());

        punch(staff, CheckInOutType.CHECK_OUT, LocalTime.of(16, 58));
        attendance.calculateTimesheet(staff.getId(), DAY);
        flush();
        assertEquals(TimesheetStatus.APPROVED, sheet(staff).getStatus());
        assertEquals(1.0, sheet(staff).getTotalWorkRatio());
    }

    @Test
    void rejectedDayGetsNoCredit() {
        Staff staff = staff();
        punch(staff, CheckInOutType.CHECK_IN, LocalTime.of(20, 47));
        attendance.calculateTimesheet(staff.getId(), DAY);
        flush();
        attendance.approve(sheet(staff).getId(), TimesheetStatus.REJECTED, "Không hợp lệ");
        flush();
        assertEquals(0.0, sheet(staff).getTotalWorkRatio());
    }

    @Test
    void approvedUnpaidLeaveStaysZero() {
        Staff staff = staff();
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setRequestStaffId(staff.getId());
        dto.setLeaveType(LeaveType.UNPAID);
        dto.setFromDate(DAY);
        dto.setToDate(DAY);
        dto.setRequestReason("Việc riêng");
        LeaveRequestDto leave = leaves.create(dto);
        leaves.approve(leave.getId(), null);
        flush();
        assertEquals(TimesheetStatus.APPROVED, sheet(staff).getStatus());
        assertEquals(0.0, sheet(staff).getTotalWorkRatio());
    }

    private Staff staff() {
        ShiftWork shift = fullDay();
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 30));
        shift.setWorkRatio(1.0);
        shifts.saveAndFlush(shift);
        Staff staff = new Staff();
        staff.setStaffCode("CREDIT_" + UUID.randomUUID());
        staff.setDisplayName("Nhân viên kiểm thử công đã duyệt");
        staff.setAnnualLeave(12.0);
        return staffs.saveAndFlush(staff);
    }

    private ShiftWork fullDay() {
        return shifts.findByCode("CA_CA_NGAY").orElseThrow();
    }

    private void punch(Staff staff, CheckInOutType type, LocalTime time) {
        CheckInOutRecord log = new CheckInOutRecord();
        log.setStaff(staff);
        log.setShift(fullDay());
        log.setRecordType(type);
        log.setRecordTime(DAY.atTime(time));
        logs.saveAndFlush(log);
    }

    private Timesheet sheet(Staff staff) {
        return sheets.findByStaffIdAndWorkingDate(staff.getId(), DAY).orElseThrow();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
