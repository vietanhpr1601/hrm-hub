package hrm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tbl_payroll_email", uniqueConstraints = @UniqueConstraint(name = "uq_payroll_email_event", columnNames = {"payslip_id", "event_type"}),
        indexes = @Index(name = "idx_payroll_email_pending", columnList = "status,create_date"))
@Getter @Setter
public class PayrollEmail extends BaseModel {
    public enum Status { PENDING, SENT, FAILED }
    public enum EventType { CONFIRMATION, PAYMENT }
    @Column(name = "payslip_id", nullable = false) private UUID payslipId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, columnDefinition = "varchar(32) default 'PAYMENT'")
    private EventType eventType = EventType.PAYMENT;
    private String recipient;
    @Column(nullable = false) private String subject;
    @Column(columnDefinition = "text", nullable = false) private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.PENDING;
    private String errorMessage;
    private LocalDateTime sentAt;
    private int attempts;
}
