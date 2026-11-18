package hrm.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.dto.request.CheckInOutRecordDto;
import hrm.dto.request.TimesheetDetailDto;
import hrm.enums.CheckInOutType;
import hrm.model.CheckInOutRecord;
import hrm.model.ShiftWork;
import hrm.model.Staff;
import hrm.model.TimesheetDetail;
import hrm.repository.*;
import hrm.service.AttendanceWorkplaceService;
import hrm.service.AttendanceLocationPolicy;
import hrm.service.TimesheetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckInOutLocationTests {
    private static final Instant NOW = Instant.parse("2026-10-03T03:14:45Z");
    private CheckInOutServiceImpl service;
    private CheckInOutRecordRepository logs;
    private StaffRepository staffs;
    private ShiftWorkRepository shifts;
    private TimesheetService timesheets;
    private TimesheetRepository sheets;
    private CheckInOutRecordDto request;
    private Staff staff;
    private ShiftWork shift;

    @BeforeEach
    void setUp() {
        service = new CheckInOutServiceImpl(Clock.fixed(NOW, ZoneOffset.UTC));
        logs = mock(CheckInOutRecordRepository.class);
        staffs = mock(StaffRepository.class);
        shifts = mock(ShiftWorkRepository.class);
        timesheets = mock(TimesheetService.class);
        ReflectionTestUtils.setField(service, "checkInOutRecordRepository", logs);
        ReflectionTestUtils.setField(service, "staffRepository", staffs);
        ReflectionTestUtils.setField(service, "shiftWorkRepository", shifts);
        ReflectionTestUtils.setField(service, "timesheetService", timesheets);
        sheets = mock(TimesheetRepository.class);
        ReflectionTestUtils.setField(service, "timesheetRepository", sheets);
        ReflectionTestUtils.setField(service, "workplaceService", new AttendanceWorkplaceService("", "", ""));
        ReflectionTestUtils.setField(service, "locationPolicy", new AttendanceLocationPolicy("50", "20", "25000"));
        staff = new Staff();
        staff.setId(UUID.randomUUID());
        shift = new ShiftWork();
        shift.setId(UUID.randomUUID());
        request = new CheckInOutRecordDto();
        request.setStaffId(staff.getId());
        request.setShiftId(shift.getId());
        request.setRecordType(CheckInOutType.CHECK_IN);
        request.setLatitude(10.77);
        request.setLongitude(106.69);
        request.setLocationAccuracy(15.0);
        request.setLocationCapturedAt(NOW.minusSeconds(5));
        when(staffs.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(shifts.findById(shift.getId())).thenReturn(Optional.of(shift));
        when(logs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validLocationSavesWithoutPhotoAndUsesServerVietnamTime() {
        CheckInOutRecordDto result = service.save(request);
        assertEquals(LocalDateTime.of(2026, 10, 3, 10, 14, 45), result.getRecordTime());
        assertEquals(15.0, result.getLocationAccuracy());
        assertEquals(request.getLocationCapturedAt(), result.getLocationCapturedAt());
        assertNull(result.getDistanceMeters());
        assertNull(result.getWorkplaceName());
        verify(timesheets).calculateTimesheet(staff.getId(), LocalDate.of(2026, 10, 3));
    }

    @Test
    void zeroCoordinatesAndZeroAccuracyAreValid() {
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        request.setLocationAccuracy(0.0);
        CheckInOutRecordDto result = service.save(request);
        assertEquals(0.0, result.getLatitude());
        assertEquals(0.0, result.getLongitude());
        assertEquals(0.0, result.getLocationAccuracy());
    }

    @Test
    void missingCoordinateRejectsWithoutSaving() {
        request.setLatitude(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        request.setLatitude(10.77);
        request.setLongitude(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        verifyNoInteractions(logs, timesheets);
    }

    @Test
    void nonFiniteAndOutOfRangeCoordinatesRejectWithoutSaving() {
        for (double latitude : new double[]{-90.01, 90.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            request.setLatitude(latitude);
            assertThrows(IllegalArgumentException.class, () -> service.save(request));
        }
        request.setLatitude(10.77);
        for (double longitude : new double[]{-180.01, 180.01, Double.NaN, Double.NEGATIVE_INFINITY}) {
            request.setLongitude(longitude);
            assertThrows(IllegalArgumentException.class, () -> service.save(request));
        }
        verifyNoInteractions(logs, timesheets);
    }

    @Test
    void accuracyRequiredAndFiniteWithoutAnyAttendanceMutation() {
        request.setLocationAccuracy(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        for (double accuracy : new double[]{-0.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            request.setLocationAccuracy(accuracy);
            assertThrows(IllegalArgumentException.class, () -> service.save(request));
        }
        verifyNoInteractions(logs, sheets, timesheets);
    }

    @Test
    void configuredAccuracyBoundaryIsAcceptedAndCoordinatesStayUnchanged() {
        request.setLocationAccuracy(50.0);
        CheckInOutRecordDto result = service.save(request);
        assertEquals(50.0, result.getLocationAccuracy());
        assertEquals(request.getLatitude(), result.getLatitude());
        assertEquals(request.getLongitude(), result.getLongitude());
    }

    @Test
    void excessiveAccuracyRejectsBothRecordTypesBeforeSavingOrRecalculating() {
        for (CheckInOutType type : CheckInOutType.values()) {
            request.setRecordType(type);
            for (double accuracy : new double[]{50.01, 5000.0}) {
                request.setLocationAccuracy(accuracy);
                var error = assertThrows(IllegalArgumentException.class, () -> service.save(request));
                assertTrue(error.getMessage().contains("50 m"));
            }
        }
        verifyNoInteractions(logs, sheets, timesheets, staffs, shifts);
    }

    @Test
    void customizedAccuracyPolicyReplacesDefaultLimit() {
        ReflectionTestUtils.setField(service, "locationPolicy", new AttendanceLocationPolicy("12.5", "10", "30000"));
        request.setLocationAccuracy(12.51);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        verifyNoInteractions(logs, sheets, timesheets);
        request.setLocationAccuracy(12.5);
        assertEquals(12.5, service.save(request).getLocationAccuracy());
    }

    @Test
    void missingStaleAndFuturePositionRejectWithoutSaving() {
        for (Instant capturedAt : new Instant[]{null, NOW.minusSeconds(121), NOW.plusSeconds(31)}) {
            request.setLocationCapturedAt(capturedAt);
            assertThrows(IllegalArgumentException.class, () -> service.save(request));
        }
        verifyNoInteractions(logs, timesheets);
    }

    @Test
    void captureTimeBoundaryIsAccepted() {
        request.setLocationCapturedAt(NOW.minusSeconds(120));
        assertNotNull(service.save(request));
        request.setLocationCapturedAt(NOW.plusSeconds(30));
        assertNotNull(service.save(request));
    }

    @Test
    void invalidStaffShiftAndRecordTypeRejectWithoutSaving() {
        request.setRecordType(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        request.setRecordType(CheckInOutType.CHECK_IN);
        request.setShiftId(null);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        request.setShiftId(shift.getId());
        shift.setIsDeleted(true);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        shift.setIsDeleted(false);
        staff.setIsDeleted(true);
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        staff.setIsDeleted(false);
        when(shifts.findById(shift.getId())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.save(request));
        verifyNoInteractions(logs, timesheets);
    }

    @Test
    void workplaceDistanceIsCalculatedByServerAndIgnoresClientSnapshot() {
        ReflectionTestUtils.setField(service, "workplaceService", new AttendanceWorkplaceService("Office", "0", "0"));
        request.setLatitude(0.0);
        request.setLongitude(1.0);
        request.setDistanceMeters(0.0);
        request.setWorkplaceName("Forged office");
        CheckInOutRecordDto result = service.save(request);
        assertEquals("Office", result.getWorkplaceName());
        assertEquals(111195.1, result.getDistanceMeters(), 0.1);
        ArgumentCaptor<CheckInOutRecord> record = ArgumentCaptor.forClass(CheckInOutRecord.class);
        verify(logs).save(record.capture());
        assertEquals(result.getDistanceMeters(), record.getValue().getDistanceMeters());
        assertEquals("Office", record.getValue().getWorkplaceName());
    }

    @Test
    void responseContractsHaveNoAttendancePhotoFields() {
        ObjectMapper mapper = new ObjectMapper();
        String logJson = mapper.valueToTree(new CheckInOutRecordDto(new CheckInOutRecord())).toString();
        String detailJson = mapper.valueToTree(new TimesheetDetailDto(new TimesheetDetail())).toString();
        assertFalse(logJson.toLowerCase().contains("photo"));
        assertFalse(detailJson.toLowerCase().contains("photo"));
    }
}
