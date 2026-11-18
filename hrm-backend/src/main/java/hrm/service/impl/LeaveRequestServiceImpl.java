package hrm.service.impl;

import hrm.dto.request.LeaveRequestDto;
import hrm.dto.response.StaffAnnualLeaveBalanceDto;
import hrm.dto.search.LeaveRequestSearchRequest;
import hrm.enums.LeaveApprovalStatus;
import hrm.enums.LeaveType;
import hrm.enums.TimesheetStatus;
import hrm.model.LeaveRequest;
import hrm.model.Staff;
import hrm.repository.LeaveRequestRepository;
import hrm.repository.ShiftWorkRepository;
import hrm.repository.StaffRepository;
import hrm.repository.TimesheetRepository;
import hrm.service.LeaveRequestService;
import hrm.service.TimesheetService;
import hrm.service.NotificationService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LeaveRequestServiceImpl implements LeaveRequestService {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ShiftWorkRepository shiftWorkRepository;

    @Autowired
    private TimesheetService timesheetService;

    @Autowired
    private TimesheetRepository timesheetRepository;

    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getRelatedToAttendance(UUID staffId, LocalDate date) {
        return leaveRequestRepository.findRelatedToAttendance(staffId, date).stream()
                .map(LeaveRequestDto::new).toList();
    }

    @Override
    public Page<LeaveRequestDto> search(LeaveRequestSearchRequest request) {
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getStaffId() != null) {
                predicates.add(cb.equal(root.get("requestStaff").get("id"), request.getStaffId()));
            }

            if (request.getLeaveType() != null) {
                predicates.add(cb.equal(root.get("leaveType"), request.getLeaveType()));
            }

            if (request.getApprovalStatus() != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), request.getApprovalStatus()));
            }

            if (request.getDepartmentId() != null) {
                predicates.add(cb.equal(root.get("requestStaff").get("department").get("id"), request.getDepartmentId()));
            }

            if (request.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fromDate"), request.getFromDate()));
            }

            if (request.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("toDate"), request.getToDate()));
            }

            if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
                String kw = "%" + request.getKeyword().toLowerCase() + "%";
                Predicate codeLike = cb.like(cb.lower(root.get("requestStaff").get("staffCode")), kw);
                Predicate nameLike = cb.like(cb.lower(root.get("requestStaff").get("displayName")), kw);
                predicates.add(cb.or(codeLike, nameLike));
            }

            predicates.add(cb.or(cb.isNull(root.get("isDeleted")), cb.equal(root.get("isDeleted"), false)));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        int pageNum = request.getPageIndex() >= 1 ? request.getPageIndex() - 1 : 0;
        int size = request.getPageSize() > 0 ? request.getPageSize() : 10;

        // Đơn mới tạo lên đầu, đơn cũ xuống cuối.
        Sort newestFirst = Sort.by(Sort.Order.desc("createDate"), Sort.Order.desc("fromDate"));
        Page<LeaveRequest> page = leaveRequestRepository.findAll(spec, PageRequest.of(pageNum, size, newestFirst));
        Map<String, Double> remainingByStaffYear = new HashMap<>();
        List<LeaveRequestDto> dtos = page.getContent().stream()
                .map(entity -> {
                    LeaveRequestDto dto = new LeaveRequestDto(entity);
                    if (entity.getRequestStaff() != null && entity.getFromDate() != null) {
                        UUID staffId = entity.getRequestStaff().getId();
                        int year = entity.getFromDate().getYear();
                        String key = staffId + ":" + year;
                        double remaining = remainingByStaffYear.computeIfAbsent(key, ignored -> {
                            Double limit = entity.getRequestStaff().getAnnualLeave();
                            if (limit == null) limit = 12.0;
                            Double used = leaveRequestRepository.calculateUsedAnnualLeave(staffId, year);
                            return limit - (used == null ? 0.0 : used);
                        });
                        dto.setRemainingAnnualLeaveDays(remaining);
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, PageRequest.of(pageNum, size), page.getTotalElements());
    }

    @Override
    public LeaveRequestDto getById(UUID id) {
        return leaveRequestRepository.findById(id)
                .filter(entity -> entity.getIsDeleted() == null || !entity.getIsDeleted())
                .map(LeaveRequestDto::new)
                .orElse(null);
    }

    @Override
    public LeaveRequestDto create(LeaveRequestDto dto) {
        validateRequest(dto);
        LeaveRequest entity = new LeaveRequest();
        mapDtoToEntity(dto, entity);
        
        // Mặc định ngày tạo đơn và trạng thái PENDING
        entity.setRequestDate(LocalDate.now());
        entity.setApprovalStatus(LeaveApprovalStatus.PENDING);
        
        // Tính tổng số ngày phép
        calculateDaysAndHours(entity);
        
        // Kiểm tra trùng lịch
        validateOverlap(entity);
        validateMakeupSchedule(entity);
        
        // Nếu là đơn nghỉ phép năm, kiểm tra xem có vượt quá định mức không
        if (entity.getLeaveType() == LeaveType.ANNUAL) {
            validateLeaveBalanceLimit(entity);
        }

        LeaveRequest saved = leaveRequestRepository.save(entity);
        return new LeaveRequestDto(saved);
    }

    @Override
    public LeaveRequestDto update(UUID id, LeaveRequestDto dto) {
        validateRequest(dto);
        LeaveRequest entity = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép"));
        
        if (entity.getApprovalStatus() != LeaveApprovalStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể cập nhật đơn nghỉ phép ở trạng thái chờ duyệt (PENDING)");
        }

        mapDtoToEntity(dto, entity);
        calculateDaysAndHours(entity);
        validateOverlap(entity);
        validateMakeupSchedule(entity);
        
        if (entity.getLeaveType() == LeaveType.ANNUAL) {
            validateLeaveBalanceLimit(entity);
        }

        LeaveRequest saved = leaveRequestRepository.save(entity);
        return new LeaveRequestDto(saved);
    }

    @Override
    public void delete(UUID id) {
        LeaveRequest entity = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép"));
        
        // Đơn đã duyệt mà bị xóa (hoặc hủy) cần cập nhật lại Timesheet
        LeaveApprovalStatus oldStatus = entity.getApprovalStatus();
        
        entity.setIsDeleted(true);
        leaveRequestRepository.save(entity);

        if (oldStatus == LeaveApprovalStatus.APPROVED) {
            updateTimesheetsForLeaveRequest(entity);
        }
    }

    @Override
    public LeaveRequestDto approve(UUID id, String rejectReason) {
        LeaveRequest entity = leaveRequestRepository.findForApproval(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép"));

        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new IllegalArgumentException("Đơn nghỉ phép đã bị xóa");
        }
        if (entity.getApprovalStatus() == LeaveApprovalStatus.APPROVED) {
            return new LeaveRequestDto(entity);
        }

        validateOverlap(entity);
        validateMakeupSchedule(entity);

        // Kiểm tra lại số dư phép năm nếu là phép ANNUAL
        if (entity.getLeaveType() == LeaveType.ANNUAL) {
            validateLeaveBalanceLimit(entity);
        }

        entity.setApprovalStatus(LeaveApprovalStatus.APPROVED);
        entity.setRejectReason(rejectReason);
        LeaveRequest saved = leaveRequestRepository.save(entity);

        // Đồng bộ dữ liệu chấm công (Timesheet)
        updateTimesheetsForLeaveRequest(saved);

        notificationService.leaveApproved(saved);

        return new LeaveRequestDto(saved);
    }

    @Override
    public LeaveRequestDto reject(UUID id, String rejectReason) {
        LeaveRequest entity = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép"));

        LeaveApprovalStatus oldStatus = entity.getApprovalStatus();

        entity.setApprovalStatus(LeaveApprovalStatus.REJECTED);
        entity.setRejectReason(rejectReason);
        LeaveRequest saved = leaveRequestRepository.save(entity);

        // Nếu trước đó đơn đã được APPROVED, nay chuyển sang REJECTED thì cần tính toán lại Timesheet để bỏ công phép
        if (oldStatus == LeaveApprovalStatus.APPROVED) {
            updateTimesheetsForLeaveRequest(saved);
        }

        return new LeaveRequestDto(saved);
    }

    @Override
    public StaffAnnualLeaveBalanceDto getLeaveBalance(UUID staffId, int year) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên"));

        Double limit = staff.getAnnualLeave();
        if (limit == null) {
            limit = 12.0;
        }

        Double used = leaveRequestRepository.calculateUsedAnnualLeave(staffId, year);
        Double remaining = limit - used;

        String deptName = staff.getDepartment() != null ? staff.getDepartment().getName() : "";
        String positionName = staff.getPosition() != null ? staff.getPosition().getName() : "";

        return StaffAnnualLeaveBalanceDto.builder()
                .staffId(staff.getId())
                .staffName(staff.getDisplayName())
                .staffCode(staff.getStaffCode())
                .departmentName(deptName)
                .positionName(positionName)
                .year(year)
                .annualLeave(limit)
                .usedDays(used)
                .remainingDays(remaining)
                .build();
    }

    private void validateRequest(LeaveRequestDto dto) {
        if (dto == null || dto.getRequestStaffId() == null || dto.getLeaveType() == null) {
            throw new IllegalArgumentException("Nhân viên và loại nghỉ phép là bắt buộc");
        }
        if (dto.getFromDate() == null || dto.getToDate() == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và kết thúc nghỉ phép là bắt buộc");
        }
        if (dto.getToDate().isBefore(dto.getFromDate())) {
            throw new IllegalArgumentException("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu");
        }
        if (Boolean.TRUE.equals(dto.getHalfDayLeave()) && !dto.getFromDate().equals(dto.getToDate())) {
            throw new IllegalArgumentException("Nghỉ nửa ngày chỉ áp dụng trong một ngày");
        }
    }

    private void mapDtoToEntity(LeaveRequestDto dto, LeaveRequest entity) {
        if (dto.getRequestStaffId() != null) {
            Staff requestStaff = staffRepository.findById(dto.getRequestStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên yêu cầu"));
            entity.setRequestStaff(requestStaff);
        }
        
        entity.setLeaveType(dto.getLeaveType());
        entity.setFromDate(dto.getFromDate());
        entity.setToDate(dto.getToDate());
        entity.setRequestReason(dto.getRequestReason());
        entity.getMakeupDates().clear();
        if (dto.getLeaveType() == LeaveType.MAKEUP && dto.getMakeupDates() != null) {
            entity.getMakeupDates().addAll(dto.getMakeupDates());
        }
        
        entity.setHalfDayLeave(dto.getHalfDayLeave() != null ? dto.getHalfDayLeave() : false);

        if (dto.getShiftWorkId() != null) {
            shiftWorkRepository.findById(dto.getShiftWorkId()).ifPresent(entity::setShiftWork);
        } else {
            entity.setShiftWork(null);
        }
    }

    private void calculateDaysAndHours(LeaveRequest request) {
        if (request.getFromDate() == null || request.getToDate() == null) {
            request.setTotalDays(0.0);
            return;
        }

        double totalDays = 0.0;
        LocalDate start = request.getFromDate();
        LocalDate end = request.getToDate();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            // Loại trừ ngày nghỉ cuối tuần (Thứ 7 & Chủ nhật)
            DayOfWeek day = date.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                continue;
            }

            double dayValue = 1.0;
            if (request.getHalfDayLeave() != null && request.getHalfDayLeave() && start.equals(end)) {
                dayValue = 0.5;
            }
            totalDays += dayValue;
        }

        request.setTotalDays(totalDays);
    }

    private void validateOverlap(LeaveRequest request) {
        List<LeaveRequest> overlaps = leaveRequestRepository.findOverlappingRequests(
                request.getRequestStaff().getId(),
                request.getFromDate(),
                request.getToDate(),
                request.getId()
        );
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("Nhân viên đã có đơn nghỉ phép trùng lặp trong khoảng thời gian này!");
        }
    }

    private void validateMakeupSchedule(LeaveRequest request) {
        List<LocalDate> dates = request.getMakeupDates();
        if (request.getLeaveType() == LeaveType.MAKEUP) {
            int requiredDates = (int) Math.ceil(request.getTotalDays());
            if (requiredDates == 0 || dates.size() != requiredDates) {
                throw new IllegalArgumentException("Cần chọn " + requiredDates
                        + " ngày làm bù tương ứng với số ngày nghỉ làm việc (nửa ngày chọn 1 ngày)");
            }
            if (dates.contains(null) || new HashSet<>(dates).size() != dates.size()) {
                throw new IllegalArgumentException("Ngày làm bù không được trống hoặc trùng nhau");
            }
            if (dates.stream().anyMatch(date -> !date.isBefore(request.getFromDate()) && !date.isAfter(request.getToDate()))) {
                throw new IllegalArgumentException("Ngày làm bù phải nằm ngoài khoảng ngày nghỉ");
            }
            dates.sort(LocalDate::compareTo);
        }
        List<LeaveRequest> others = leaveRequestRepository.findActiveRequestsByStaff(request.getRequestStaff().getId());
        for (LeaveRequest other : others) {
            if (other.getId().equals(request.getId())) continue;
            for (LocalDate date : dates) {
                if ((!date.isBefore(other.getFromDate()) && !date.isAfter(other.getToDate()))
                        || other.getMakeupDates().contains(date)) {
                    throw new IllegalArgumentException("Ngày làm bù " + date + " đã có đơn nghỉ hoặc lịch làm bù khác");
                }
            }
            if (other.getMakeupDates().stream().anyMatch(date -> !date.isBefore(request.getFromDate()) && !date.isAfter(request.getToDate()))) {
                throw new IllegalArgumentException("Khoảng ngày nghỉ trùng với ngày làm bù đã đăng ký");
            }
        }
    }

    private void validateLeaveBalanceLimit(LeaveRequest request) {
        int year = request.getFromDate().getYear();
        Double used = leaveRequestRepository.calculateUsedAnnualLeave(request.getRequestStaff().getId(), year);
        Double limit = request.getRequestStaff().getAnnualLeave();
        if (limit == null) {
            limit = 12.0;
        }

        if (used + request.getTotalDays() > limit) {
            throw new IllegalArgumentException(String.format(
                "Không đủ phép năm còn lại! Định mức: %.1f, Đã dùng: %.1f, Đăng ký mới: %.1f", 
                limit, used, request.getTotalDays()
            ));
        }
    }

    private void updateTimesheetsForLeaveRequest(LeaveRequest request) {
        if (request.getRequestStaff() == null) return;
        UUID staffId = request.getRequestStaff().getId();
        LocalDate start = request.getFromDate();
        LocalDate end = request.getToDate();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                continue;
            }
            // Duyệt nghỉ không phải là duyệt các lượt đi làm bị che bởi đơn nghỉ.
            // Khi thu hồi đơn, công tính lại phải được người duyệt kiểm tra riêng.
            if (Boolean.TRUE.equals(request.getIsDeleted())
                    || request.getApprovalStatus() != LeaveApprovalStatus.APPROVED) {
                timesheetRepository.findByStaffIdAndWorkingDate(staffId, date).ifPresent(timesheet -> {
                    timesheet.setStatus(TimesheetStatus.SUBMITTED);
                    timesheet.setNote("Đơn nghỉ đã hủy/từ chối; công tính lại cần được duyệt.");
                });
            }
            // Cập nhật công và quyết định duyệt trong cùng giao dịch.
            timesheetService.calculateTimesheet(staffId, date);
        }
    }

    @Override
    public Page<StaffAnnualLeaveBalanceDto> getLeaveBalances(hrm.dto.search.SearchDto searchDto, int year) {
        List<Staff> filteredList = staffRepository.findAll().stream()
                .filter(staff -> staff.getIsDeleted() == null || !staff.getIsDeleted())
                .filter(staff -> {
                    if (searchDto != null) {
                        if (searchDto.getKeyword() != null && !searchDto.getKeyword().isEmpty()) {
                            String keyword = searchDto.getKeyword().toLowerCase();
                            boolean matches = (staff.getStaffCode() != null && staff.getStaffCode().toLowerCase().contains(keyword))
                                || (staff.getDisplayName() != null && staff.getDisplayName().toLowerCase().contains(keyword))
                                || (staff.getEmail() != null && staff.getEmail().toLowerCase().contains(keyword))
                                || (staff.getPhoneNumber() != null && staff.getPhoneNumber().contains(keyword));
                            if (!matches) return false;
                        }
                        if (searchDto.getDepartmentId() != null) {
                            if (staff.getDepartment() == null || !staff.getDepartment().getId().equals(searchDto.getDepartmentId())) {
                                return false;
                            }
                        }
                        if (searchDto.getPositionId() != null) {
                            if (staff.getPosition() == null || !staff.getPosition().getId().equals(searchDto.getPositionId())) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        int total = filteredList.size();
        int pageNum = 0;
        int size = 10;

        if (searchDto != null) {
            pageNum = searchDto.getPageIndex() >= 1 ? searchDto.getPageIndex() - 1 : 0;
            size = searchDto.getPageSize() > 0 ? searchDto.getPageSize() : 10;
        }

        int fromIndex = pageNum * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<StaffAnnualLeaveBalanceDto> pageContent = new ArrayList<>();
        if (fromIndex < total) {
            for (Staff staff : filteredList.subList(fromIndex, toIndex)) {
                Double limit = staff.getAnnualLeave();
                if (limit == null) {
                    limit = 12.0;
                }
                Double used = leaveRequestRepository.calculateUsedAnnualLeave(staff.getId(), year);
                Double remaining = limit - used;
                String deptName = staff.getDepartment() != null ? staff.getDepartment().getName() : "";
                String positionName = staff.getPosition() != null ? staff.getPosition().getName() : "";

                pageContent.add(StaffAnnualLeaveBalanceDto.builder()
                        .staffId(staff.getId())
                        .staffName(staff.getDisplayName())
                        .staffCode(staff.getStaffCode())
                        .departmentName(deptName)
                        .positionName(positionName)
                        .year(year)
                        .annualLeave(limit)
                        .usedDays(used)
                        .remainingDays(remaining)
                        .build());
            }
        }

        return new PageImpl<>(pageContent, PageRequest.of(pageNum, size), total);
    }
}
