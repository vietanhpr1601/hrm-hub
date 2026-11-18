package hrm.model;

import hrm.enums.PaidStatus;
import hrm.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

@Entity
@Table(name = "tbl_payslip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payslip extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_id", nullable = false)
    private Payroll payroll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "total_work_days", nullable = false)
    private Double totalWorkDays = 0.0;

    @Column(name = "total_ot_hours", nullable = false)
    private Double totalOtHours = 0.0;

    @Column(name = "total_income", nullable = false)
    private Double totalIncome = 0.0;

    @Column(name = "total_deduction", nullable = false)
    private Double totalDeduction = 0.0;

    @Column(name = "net_salary", nullable = false)
    private Double netSalary = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_status", nullable = false)
    private PaidStatus paidStatus = PaidStatus.UNPAID;

    @Column(name = "note")
    private String note;

    @Column(name = "standard_work_days")
    private Double standardWorkDays;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "paid_amount")
    private Double paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "paid_by", length = 100)
    private String paidBy;

    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayslipItem> items = new ArrayList<>();
}
