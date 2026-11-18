package hrm.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollMailGatewayTests {
    @Test void composesSingleRecipientMessageWithoutUsingRealSmtp() {
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(sender); when(provider.getObject()).thenReturn(sender);
        var gateway = new PayrollMailGateway(provider, true, "hr@example.test", "smtp.example.test");
        gateway.send("staff@example.test", "Thanh toán lương", "Đã ghi nhận 1.000 VND");
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertArrayEquals(new String[]{"staff@example.test"}, message.getValue().getTo());
        assertNull(message.getValue().getCc()); assertNull(message.getValue().getBcc());
        assertEquals("hr@example.test", message.getValue().getFrom());
        assertEquals("Đã ghi nhận 1.000 VND", message.getValue().getText());
        var disabled = new PayrollMailGateway(provider, false, "hr@example.test", "smtp.example.test");
        assertFalse(disabled.ready());
        assertThrows(IllegalStateException.class, () -> disabled.send("staff@example.test", "Subject", "Body"));
        verifyNoMoreInteractions(sender);
    }
}
