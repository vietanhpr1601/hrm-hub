package hrm.model;

import hrm.enums.SalaryCalculationType;
import hrm.enums.SalaryItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_payslip_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayslipItem extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Payslip payslip;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "salary_item_id", nullable = false)
    private SalaryItem salaryItem;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "calculated_value", nullable = false)
    private Double calculatedValue = 0.0;

    @Column(name = "amount")
    private Double amount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type")
    private SalaryCalculationType calculationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type")
    private SalaryItemType type;

    public SalaryCalculationType getCalculationType() {
        return calculationType != null ? calculationType
                : salaryItem != null ? salaryItem.getCalculationType() : SalaryCalculationType.FIXED;
    }

    public SalaryItemType getType() {
        return type != null ? type : salaryItem != null ? salaryItem.getType() : null;
    }
}
