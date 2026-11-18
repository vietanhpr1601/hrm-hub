package hrm.service.impl;

import hrm.dto.response.DashboardSummaryResponse;
import hrm.enums.CandidateStatus;
import hrm.enums.LeaveApprovalStatus;
import hrm.enums.TimesheetStatus;
import hrm.model.Department;
import hrm.model.LeaveRequest;
import hrm.model.Staff;
import hrm.repository.*;
import hrm.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final Clock clock;

    public DashboardServiceImpl() {
        this(Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    DashboardServiceImpl(Clock clock) {
        this.clock = clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TimesheetRepository timesheetRepository;

    @Override
    public DashboardSummaryResponse getSummary() {
        LocalDate today = LocalDate.now(clock);
        // Deleted records do not represent employment events. Keep departed employees
        // whose personnel records are retained, including their historical start dates.
        List<Staff> activeStaff = staffRepository.findActiveStaffs().stream()
                .filter(staff -> !Boolean.TRUE.equals(staff.getIsDeleted())).toList();

        // 1. KPI Summary
        long totalStaff = staffRepository.countAllActiveStaff();
        
        long pendingTimesheetsToday = timesheetRepository.countByWorkingDateAndStatusAndNotDeleted(today, TimesheetStatus.SUBMITTED);
        long pendingTimesheetsYesterday = timesheetRepository.countByWorkingDateAndStatusAndNotDeleted(today.minusDays(1), TimesheetStatus.SUBMITTED);

        LocalDate startOfThisMonth = today.withDayOfMonth(1);
        long newStaffThisMonth = countJoined(activeStaff, startOfThisMonth, today);
        
        LocalDate startOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate endOfLastMonth = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        long newStaffLastMonth = countJoined(activeStaff, startOfLastMonth, endOfLastMonth);

        long pendingLeavesCount = leaveRequestRepository.countByApprovalStatusAndNotDeleted(LeaveApprovalStatus.PENDING);

        // 6-month sparkline of new hires
        List<Long> sparkline = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate start = today.minusMonths(i).withDayOfMonth(1);
            LocalDate end = i == 0 ? today : today.minusMonths(i).with(TemporalAdjusters.lastDayOfMonth());
            sparkline.add(countJoined(activeStaff, start, end));
        }

        DashboardSummaryResponse.KpiSummary kpiSummary = DashboardSummaryResponse.KpiSummary.builder()
                .totalStaff(totalStaff)
                .newStaffThisMonth(newStaffThisMonth)
                .newStaffLastMonth(newStaffLastMonth)
                .pendingTimesheetsToday(pendingTimesheetsToday)
                .pendingTimesheetsYesterday(pendingTimesheetsYesterday)
                .pendingLeaveRequests(pendingLeavesCount)
                .staffTrendSpark(sparkline)
                .build();

        // 2. Staff Trend (12 Months)
        List<DashboardSummaryResponse.MonthlyStaffTrend> staffTrend = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth month = YearMonth.from(today).minusMonths(i);
            LocalDate start = month.atDay(1);
            LocalDate end = i == 0 ? today : month.atEndOfMonth();
            long newHires = countJoined(activeStaff, start, end);
            long resignations = activeStaff.stream()
                    .filter(s -> "5".equals(s.getWorkingStatus()))
                    .filter(s -> isBetween(s.getResignationDate(), start, end))
                    .count();

            staffTrend.add(DashboardSummaryResponse.MonthlyStaffTrend.builder()
                    .period(month.toString())
                    .month(month.format(DateTimeFormatter.ofPattern("MM/yyyy")))
                    .newHires(newHires)
                    .resignations(resignations)
                    .build());
        }
        DashboardSummaryResponse.StaffTrendMetadata staffTrendMetadata = DashboardSummaryResponse.StaffTrendMetadata.builder()
                .asOfDate(today)
                .unknownStartDates(activeStaff.stream().filter(this::hasJoined).filter(staff -> staff.getStartDate() == null).count())
                .unknownResignationDates(activeStaff.stream().filter(staff -> "5".equals(staff.getWorkingStatus()))
                        .filter(staff -> staff.getResignationDate() == null).count())
                .build();

        // 3. Department Distribution
        List<Department> departments = departmentRepository.findAll();
        long totalActive = activeStaff.size();

        List<DashboardSummaryResponse.DepartmentDistribution> departmentDistribution = new ArrayList<>();
        for (Department dept : departments) {
            long count = activeStaff.stream()
                    .filter(s -> s.getDepartment() != null && s.getDepartment().getId().equals(dept.getId()))
                    .count();
            if (count > 0) {
                double pct = totalActive > 0 ? (double) count / totalActive * 100.0 : 0.0;
                pct = Math.round(pct * 10.0) / 10.0;
                departmentDistribution.add(DashboardSummaryResponse.DepartmentDistribution.builder()
                        .departmentId(dept.getId())
                        .departmentName(dept.getName())
                        .staffCount(count)
                        .percentage(pct)
                        .build());
            }
        }
        // Sort descending by staffCount
        departmentDistribution.sort((d1, d2) -> Long.compare(d2.getStaffCount(), d1.getStaffCount()));
        
        // Group "Khác" if > 5 departments
        if (departmentDistribution.size() > 5) {
            List<DashboardSummaryResponse.DepartmentDistribution> top5 = new ArrayList<>(departmentDistribution.subList(0, 5));
            long otherCount = 0;
            double otherPct = 0.0;
            for (int i = 5; i < departmentDistribution.size(); i++) {
                otherCount += departmentDistribution.get(i).getStaffCount();
                otherPct += departmentDistribution.get(i).getPercentage();
            }
            top5.add(DashboardSummaryResponse.DepartmentDistribution.builder()
                    .departmentId(null)
                    .departmentName("Khác")
                    .staffCount(otherCount)
                    .percentage(Math.round(otherPct * 10.0) / 10.0)
                    .build());
            departmentDistribution = top5;
        }

        // 4. Recent Activities (Aggregate from multiple tables)
        List<DashboardSummaryResponse.RecentActivityItem> recentActivities = new ArrayList<>();

        // Add recent staff additions
        List<Staff> allStaff = new ArrayList<>(activeStaff);
        allStaff.sort((s1, s2) -> {
            LocalDateTime t1 = s1.getCreateDate() != null ? s1.getCreateDate() : LocalDateTime.now();
            LocalDateTime t2 = s2.getCreateDate() != null ? s2.getCreateDate() : LocalDateTime.now();
            return t2.compareTo(t1);
        });
        allStaff.stream().limit(5).forEach(s -> {
            LocalDateTime ts = s.getCreateDate() != null ? s.getCreateDate() : 
                              (s.getStartDate() != null ? s.getStartDate().atStartOfDay() : LocalDateTime.now());
            recentActivities.add(DashboardSummaryResponse.RecentActivityItem.builder()
                    .type("STAFF")
                    .title("Nhân sự mới")
                    .description("Nhân viên " + s.getDisplayName() + " (" + s.getStaffCode() + ") đã được thêm vào hệ thống.")
                    .staffName(s.getDisplayName())
                    .staffAvatar(s.getAvatarUrl())
                    .timestamp(ts)
                    .build());
        });

        // Add recent leave requests
        List<LeaveRequest> recentLeavesList = leaveRequestRepository.findRecentLeaves(PageRequest.of(0, 5));
        recentLeavesList.forEach(l -> {
            LocalDateTime ts = l.getCreateDate() != null ? l.getCreateDate() : LocalDateTime.now();
            String statusText = l.getApprovalStatus() == LeaveApprovalStatus.PENDING ? "Đang chờ duyệt" : 
                               l.getApprovalStatus() == LeaveApprovalStatus.APPROVED ? "Đã duyệt" : "Bị từ chối";
            recentActivities.add(DashboardSummaryResponse.RecentActivityItem.builder()
                    .type("LEAVE")
                    .title("Yêu cầu nghỉ phép")
                    .description(l.getRequestStaff().getDisplayName() + " đăng ký nghỉ " + l.getLeaveType() + " (" + l.getTotalDays() + " ngày) - " + statusText)
                    .staffName(l.getRequestStaff().getDisplayName())
                    .staffAvatar(l.getRequestStaff().getAvatarUrl())
                    .timestamp(ts)
                    .build());
        });

        // Sort recentActivities combined by timestamp DESC
        recentActivities.sort((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()));
        List<DashboardSummaryResponse.RecentActivityItem> limitedActivities = recentActivities.stream()
                .limit(8)
                .collect(Collectors.toList());

        // 5. Pending Leaves List
        List<LeaveRequest> pendingLeavesList = leaveRequestRepository.findRecentLeaves(PageRequest.of(0, 15));
        pendingLeavesList.removeIf(l -> l.getApprovalStatus() != LeaveApprovalStatus.PENDING);
        List<DashboardSummaryResponse.PendingLeaveItem> pendingLeaves = pendingLeavesList.stream()
                .limit(5)
                .map(l -> DashboardSummaryResponse.PendingLeaveItem.builder()
                        .leaveId(l.getId())
                        .staffName(l.getRequestStaff().getDisplayName())
                        .staffAvatar(l.getRequestStaff().getAvatarUrl())
                        .leaveType(l.getLeaveType().name())
                        .fromDate(l.getFromDate())
                        .toDate(l.getToDate())
                        .totalDays(l.getTotalDays())
                        .requestDate(l.getRequestDate())
                        .build())
                .collect(Collectors.toList());

        // 6. Recruitment Pipeline
        long screening = candidateRepository.countByStatusAndNotDeleted(CandidateStatus.SCREENING);
        long interview = candidateRepository.countByStatusAndNotDeleted(CandidateStatus.INTERVIEW);
        long qualified = candidateRepository.countByStatusAndNotDeleted(CandidateStatus.QUALIFIED);
        long waiting = candidateRepository.countByStatusAndNotDeleted(CandidateStatus.WAITING);
        long onboarded = candidateRepository.countByStatusAndNotDeleted(CandidateStatus.ONBOARDED);

        DashboardSummaryResponse.RecruitmentPipeline recruitmentPipeline = DashboardSummaryResponse.RecruitmentPipeline.builder()
                .screening(screening)
                .interview(interview)
                .qualified(qualified)
                .waiting(waiting)
                .onboarded(onboarded)
                .build();

        return DashboardSummaryResponse.builder()
                .kpiSummary(kpiSummary)
                .staffTrend(staffTrend)
                .staffTrendMetadata(staffTrendMetadata)
                .departmentDistribution(departmentDistribution)
                .recentActivities(limitedActivities)
                .pendingLeaves(pendingLeaves)
                .recruitmentPipeline(recruitmentPipeline)
                .build();
    }

    private boolean hasJoined(Staff staff) {
        return !"1".equals(staff.getWorkingStatus()) && !"4".equals(staff.getWorkingStatus());
    }

    private boolean isBetween(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    private long countJoined(List<Staff> staff, LocalDate start, LocalDate end) {
        return staff.stream().filter(this::hasJoined)
                .filter(item -> isBetween(item.getStartDate(), start, end)).count();
    }
}
