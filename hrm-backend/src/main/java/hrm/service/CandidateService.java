package hrm.service;

import hrm.dto.request.CandidateDto;
import hrm.dto.request.StaffDto;
import hrm.dto.search.SearchCandidateDto;
import hrm.enums.CandidateStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface CandidateService {
    Page<CandidateDto> pagingCandidates(SearchCandidateDto searchDto);
    CandidateDto getById(UUID id);
    CandidateDto saveCandidate(CandidateDto dto);
    void deleteCandidate(UUID id);
    void deleteMultiple(List<UUID> ids);
    boolean isValidCode(CandidateDto dto);
    String generateCode();
    Boolean updateStatus(UUID id, CandidateStatus status, String refusalReason);
    StaffDto convertToReceivedJob(UUID id);
}
