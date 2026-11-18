package hrm.dto.request;

import hrm.enums.SalaryCalculationType;
import hrm.enums.SalaryItemType;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PayslipDraftRequest {
    private List<Item> items;
    private String note;

    @Data
    public static class Item {
        private UUID salaryItemId;
        private Double amount;
        private SalaryCalculationType calculationType;
        private SalaryItemType type;
    }
}
