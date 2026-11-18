package hrm.service;

import hrm.dto.request.LeaveRequestDto;
import hrm.dto.response.StaffAnnualLeaveBalanceDto;
import hrm.dto.search.LeaveRequestSearchRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;
import java.util.List;
import java.time.LocalDate;

public interface LeaveRequestService {
    List<LeaveRequestDto> getRelatedToAttendance(UUID staffId, LocalDate date);
    Page<LeaveRequestDto> search(LeaveRequestSearchRequest request);
    LeaveRequestDto getById(UUID id);
    LeaveRequestDto create(LeaveRequestDto dto);
    LeaveRequestDto update(UUID id, LeaveRequestDto dto);
    void delete(UUID id);
    LeaveRequestDto approve(UUID id, String rejectReason);
    LeaveRequestDto reject(UUID id, String rejectReason);
    StaffAnnualLeaveBalanceDto getLeaveBalance(UUID staffId, int year);
    Page<StaffAnnualLeaveBalanceDto> getLeaveBalances(hrm.dto.search.SearchDto searchDto, int year);
}
