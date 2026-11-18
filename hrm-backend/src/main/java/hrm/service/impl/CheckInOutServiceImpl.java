package hrm.service.impl;

import hrm.dto.request.CheckInOutRecordDto;
import hrm.model.CheckInOutRecord;
import hrm.model.ShiftWork;
import hrm.model.Staff;
import hrm.repository.CheckInOutRecordRepository;
import hrm.repository.ShiftWorkRepository;
import hrm.repository.StaffRepository;
import hrm.service.CheckInOutService;
import hrm.service.TimesheetService;
import hrm.service.AttendanceWorkplaceService;
import hrm.service.AttendanceLocationPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CheckInOutServiceImpl implements CheckInOutService {

    private final Clock clock;

    public CheckInOutServiceImpl() {
        this(Clock.systemUTC());
    }

    CheckInOutServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Autowired
    private CheckInOutRecordRepository checkInOutRecordRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ShiftWorkRepository shiftWorkRepository;

    @Autowired
    private AttendanceWorkplaceService workplaceService;

    @Autowired
    private AttendanceLocationPolicy locationPolicy;

    @Autowired
    @Lazy
    private TimesheetService timesheetService;

    @Autowired
    private hrm.repository.TimesheetRepository timesheetRepository;

    @Override
    public CheckInOutRecordDto save(CheckInOutRecordDto dto) {
        validateLocation(dto);
        if (dto.getStaffId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn nhân viên chấm công.");
        }
        if (dto.getRecordType() == null) {
            throw new IllegalArgumentException("Vui lòng chọn chấm công vào hoặc ra.");
        }
        if (dto.getShiftId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn ca làm việc.");
        }

        Staff staff = staffRepository.findById(dto.getStaffId())
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên đang hoạt động."));
        ShiftWork shift = shiftWorkRepository.findById(dto.getShiftId())
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Ca làm việc không tồn tại hoặc đã bị xóa."));

        LocalDateTime recordTime = dto.getRecordTime() != null ? dto.getRecordTime()
                : LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        recordTime = recordTime.withNano(0);

        timesheetRepository.findByStaffIdAndWorkingDate(staff.getId(), recordTime.toLocalDate()).ifPresent(timesheet -> {
            if (timesheet.getStatus() == hrm.enums.TimesheetStatus.APPROVED
                    || timesheet.getStatus() == hrm.enums.TimesheetStatus.REJECTED) {
                timesheet.setStatus(hrm.enums.TimesheetStatus.SUBMITTED);
            }
        });

        CheckInOutRecord record = new CheckInOutRecord();
        record.setStaff(staff);
        record.setRecordTime(recordTime);
        
        record.setIpAddress(dto.getIpAddress());
        record.setLatitude(dto.getLatitude());
        record.setLongitude(dto.getLongitude());
        record.setLocationAccuracy(dto.getLocationAccuracy());
        record.setLocationCapturedAt(dto.getLocationCapturedAt());
        AttendanceWorkplaceService.DistanceSnapshot distance = workplaceService.getDistance(dto.getLatitude(), dto.getLongitude());
        record.setDistanceMeters(distance.distanceMeters());
        record.setWorkplaceName(distance.workplaceName());
        record.setDeviceType(dto.getDeviceType());
        record.setRecordType(dto.getRecordType());
        record.setShift(shift);

        CheckInOutRecord saved = checkInOutRecordRepository.save(record);

        // Kích hoạt tính toán bảng công cho nhân viên vào ngày tương ứng
        timesheetService.calculateTimesheet(staff.getId(), recordTime.toLocalDate());

        return new CheckInOutRecordDto(saved);
    }

    private void validateLocation(CheckInOutRecordDto dto) {
        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            throw new IllegalArgumentException("Vui lòng bật và cấp quyền định vị trước khi chấm công.");
        }
        if (!Double.isFinite(dto.getLatitude()) || dto.getLatitude() < -90 || dto.getLatitude() > 90
                || !Double.isFinite(dto.getLongitude()) || dto.getLongitude() < -180 || dto.getLongitude() > 180) {
            throw new IllegalArgumentException("Tọa độ định vị không hợp lệ. Vui lòng lấy lại vị trí.");
        }
        if (dto.getLocationAccuracy() == null || !Double.isFinite(dto.getLocationAccuracy())
                || dto.getLocationAccuracy() < 0) {
            throw new IllegalArgumentException("Thiếu hoặc sai độ chính xác định vị. Vui lòng lấy lại vị trí.");
        }
        locationPolicy.requireAcceptableAccuracy(dto.getLocationAccuracy());
        Instant capturedAt = dto.getLocationCapturedAt();
        Instant now = clock.instant();
        if (capturedAt == null || capturedAt.isBefore(now.minusSeconds(120)) || capturedAt.isAfter(now.plusSeconds(30))) {
            throw new IllegalArgumentException("Vị trí đã hết hạn hoặc thời gian định vị không hợp lệ. Vui lòng lấy lại vị trí.");
        }
    }

    @Override
    public List<CheckInOutRecordDto> getRawLogs(UUID staffId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59, 999999999);
        return checkInOutRecordRepository.findByStaffIdAndRecordTimeBetweenOrderByRecordTimeAsc(staffId, start, end)
                .stream()
                .map(CheckInOutRecordDto::new)
                .collect(Collectors.toList());
    }
}
