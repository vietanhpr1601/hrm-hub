package hrm.config;

import hrm.repository.PayrollEmailRepository;
import hrm.service.PayrollEmailService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class PayrollEmailScheduler {
    private final PayrollEmailRepository emails;
    private final PayrollEmailService service;
    public PayrollEmailScheduler(PayrollEmailRepository emails, PayrollEmailService service) { this.emails = emails; this.service = service; }
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void sendPending() {
        for (var id : emails.findIdsByStatus(hrm.model.PayrollEmail.Status.PENDING, PageRequest.of(0, 20))) {
            try { service.deliver(id); }
            catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Payroll email worker could not update a queued item"); }
        }
    }
}
