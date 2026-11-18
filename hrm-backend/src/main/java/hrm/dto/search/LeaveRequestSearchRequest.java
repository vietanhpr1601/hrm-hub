package hrm.dto.search;

import hrm.enums.LeaveApprovalStatus;
import hrm.enums.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LeaveRequestSearchRequest extends SearchDto {
    private UUID staffId;
    private LeaveType leaveType;
    private LeaveApprovalStatus approvalStatus;
    private LocalDate fromDate;
    private LocalDate toDate;
}
