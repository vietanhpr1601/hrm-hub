package hrm.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import hrm.dto.request.StaffDto;
import hrm.dto.response.StaffProfileDto;
import hrm.dto.search.SearchDto;

import org.springframework.data.domain.Page;

public interface StaffService {
    StaffProfileDto getCurrentStaffProfile();
    Page<StaffDto> getAllStaffs(SearchDto searchDto);
    List<StaffDto> getAllStaffsUnpaginated();
    Optional<StaffDto> getStaffById(UUID id);
    StaffDto saveStaff(StaffDto staffDto);
    void deleteStaff(UUID id);
    boolean existsById(UUID id);
    String generateStaffCode();
}
