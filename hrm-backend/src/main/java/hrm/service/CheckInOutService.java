package hrm.service;

import hrm.dto.request.CheckInOutRecordDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CheckInOutService {
    CheckInOutRecordDto save(CheckInOutRecordDto dto);
    List<CheckInOutRecordDto> getRawLogs(UUID staffId, LocalDate date);
}
