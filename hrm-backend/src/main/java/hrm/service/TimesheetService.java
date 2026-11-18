package hrm.service;

import hrm.dto.request.TimesheetDto;
import hrm.dto.search.TimesheetSearchRequest;
import hrm.enums.TimesheetStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimesheetService {
    Page<TimesheetDto> search(TimesheetSearchRequest request);
    TimesheetDto getById(UUID id);
    TimesheetDto saveOrUpdate(TimesheetDto dto);
    boolean approve(UUID id, TimesheetStatus status, String note);
    void calculateTimesheet(UUID staffId, LocalDate date);
    List<TimesheetDto> getByStaffAndDateRange(UUID staffId, LocalDate start, LocalDate end);
}
