package hrm;

import hrm.dto.request.StaffDto;
import hrm.service.StaffService;
import hrm.repository.StaffRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@HrmIntegrationTest
class StaffResignationDateTests {
    @Autowired StaffService service;
    @Autowired StaffRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void actualResignationDateSurvivesSaveReadAndUnrelatedProfileEdits() {
        StaffDto dto = staff();
        dto.setResignationDate(LocalDate.of(2026, 1, 31));
        StaffDto saved = service.saveStaff(dto);
        flush();
        StaffDto read = service.getStaffById(saved.getId()).orElseThrow();
        assertEquals(dto.getResignationDate(), read.getResignationDate());
        read.setDisplayName("Tên đã sửa sau khi nghỉ");
        service.saveStaff(read);
        flush();
        assertEquals(dto.getResignationDate(), repository.findById(saved.getId()).orElseThrow().getResignationDate());
    }

    @Test
    void legacyOrImportedResignedStaffMayHaveUnknownDateWithoutFabricatedDefault() {
        StaffDto saved = service.saveStaff(staff());
        flush();
        assertNull(service.getStaffById(saved.getId()).orElseThrow().getResignationDate());
    }

    @Test
    void resignationBeforeStartDateIsRejectedWithoutSaving() {
        StaffDto dto = staff();
        dto.setResignationDate(dto.getStartDate().minusDays(1));
        long before = repository.count();
        assertThrows(IllegalArgumentException.class, () -> service.saveStaff(dto));
        assertEquals(before, repository.count());
    }

    @Test
    void futureDateCannotBeRecordedAsAnActualResignation() {
        StaffDto dto = staff();
        dto.setResignationDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1));
        long before = repository.count();
        assertThrows(IllegalArgumentException.class, () -> service.saveStaff(dto));
        assertEquals(before, repository.count());
    }

    private StaffDto staff() {
        StaffDto dto = new StaffDto();
        dto.setStaffCode("RESIGN_" + UUID.randomUUID());
        dto.setDisplayName("Nhân viên kiểm thử ngày nghỉ");
        dto.setWorkingStatus("5");
        dto.setStartDate(LocalDate.of(2025, 1, 1));
        return dto;
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
