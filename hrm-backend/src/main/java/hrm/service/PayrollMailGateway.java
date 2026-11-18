package hrm.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PayrollMailGateway {
    private final ObjectProvider<JavaMailSender> sender;
    private final boolean enabled;
    private final String from;
    private final String host;
    public PayrollMailGateway(ObjectProvider<JavaMailSender> sender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:}") String from, @Value("${spring.mail.host:}") String host) {
        this.sender = sender; this.enabled = enabled; this.from = from; this.host = host;
    }
    public boolean ready() { return enabled && !host.isBlank() && PayrollEmailService.validEmail(from) && sender.getIfAvailable() != null; }
    public void send(String to, String subject, String content) {
        if (!ready()) throw new IllegalStateException("Email chưa được cấu hình");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(to); message.setSubject(subject); message.setText(content);
        sender.getObject().send(message);
    }
}
