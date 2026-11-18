package hrm.service;

import hrm.dto.request.RecruitmentDto;
import hrm.dto.search.SearchRecruitmentDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface RecruitmentService {
    Page<RecruitmentDto> pagingRecruitment(SearchRecruitmentDto searchDto);
    RecruitmentDto getById(UUID id);
    RecruitmentDto saveRecruitment(RecruitmentDto dto);
    void deleteRecruitment(UUID id);
    void deleteMultipleRecruitment(List<UUID> ids);
    boolean isValidCode(RecruitmentDto dto);
    String generateCode();
}
