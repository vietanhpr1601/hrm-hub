package hrm.dto.request;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PayrollStaffRequest {
    private List<UUID> staffIds;
}
