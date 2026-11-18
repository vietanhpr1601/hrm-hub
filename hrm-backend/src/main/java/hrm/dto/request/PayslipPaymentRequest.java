package hrm.dto.request;

import hrm.enums.PaymentMethod;
import lombok.Data;
import java.time.LocalDate;

@Data
public class PayslipPaymentRequest {
    private LocalDate paidDate;
    private PaymentMethod paymentMethod;
    private String paymentReference;
    private String note;
}
