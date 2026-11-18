package hrm.service;

import hrm.dto.request.DepartmentDto;
import hrm.dto.search.SearchDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface DepartmentService {
    Page<DepartmentDto> pagingDepartments(SearchDto searchDto);
    List<DepartmentDto> getAllDepartments();
    DepartmentDto getById(UUID id);
    DepartmentDto saveDepartment(DepartmentDto dto);
    void deleteDepartment(UUID id);
    void deleteMultiple(List<UUID> ids);
    boolean isValidCode(DepartmentDto dto);
    String generateCode();
}
