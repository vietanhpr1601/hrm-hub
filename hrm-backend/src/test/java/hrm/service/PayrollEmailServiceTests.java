package hrm.service;

import hrm.enums.*;
import hrm.model.*;
import hrm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayrollEmailServiceTests {
    PayrollEmailRepository emails = mock(PayrollEmailRepository.class);
    PayslipRepository payslips = mock(PayslipRepository.class);
    PayrollMailGateway gateway = mock(PayrollMailGateway.class);
    PayrollEmailService service = new PayrollEmailService(emails, payslips, gateway);
    Payslip slip;
    @BeforeEach void setup() {
        Staff staff = new Staff(); staff.setDisplayName("Nhân viên thử nghiệm"); staff.setEmail("personal@example.test"); staff.setCompanyEmail("work@example.test");
        Period period = new Period(); period.setName("Tháng 10/2026");
        Payroll payroll = new Payroll(); payroll.setName("Lương tháng 10"); payroll.setPeriod(period); payroll.setStatus(PayrollStatus.CONFIRMED);
        slip = new Payslip(); slip.setId(UUID.randomUUID()); slip.setStaff(staff); slip.setPayroll(payroll);
        slip.setPaidStatus(PaidStatus.PAID); slip.setPaidAmount(13000000.0); slip.setPaidDate(LocalDate.of(2026, 10, 26)); slip.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        when(payslips.findById(slip.getId())).thenReturn(Optional.of(slip));
    }
    @Test void queuesOnlyPaidSlipsOnceWithSnapshotAndCompanyRecipient() {
        service.enqueuePayment(slip);
        var saved = org.mockito.ArgumentCaptor.forClass(PayrollEmail.class);
        verify(emails).save(saved.capture());
        PayrollEmail job = saved.getValue();
        assertEquals("work@example.test", job.getRecipient());
        assertEquals(PayrollEmail.Status.PENDING, job.getStatus());
        assertTrue(job.getContent().contains("13.000.000 VND"));
        assertTrue(job.getContent().contains("2026-10-26"));
        verifyNoInteractions(gateway);
        when(emails.findByPayslipIdAndEventType(slip.getId(), PayrollEmail.EventType.PAYMENT)).thenReturn(Optional.of(job));
        service.enqueuePayment(slip);
        verify(emails, times(1)).save(any());
        slip.setPaidStatus(PaidStatus.UNPAID);
        assertThrows(IllegalStateException.class, () -> service.enqueuePayment(slip));
    }
    @Test void personalEmailIsFallbackAndInvalidAddressIsVisibleFailureWithoutSending() {
        slip.getStaff().setCompanyEmail(null);
        service.enqueuePayment(slip);
        var saved = org.mockito.ArgumentCaptor.forClass(PayrollEmail.class); verify(emails).save(saved.capture());
        assertEquals("personal@example.test", saved.getValue().getRecipient());
        reset(emails);
        slip.getStaff().setEmail("bad\r\nBcc: somebody@example.test");
        service.enqueuePayment(slip);
        verify(emails).save(saved.capture());
        assertEquals(PayrollEmail.Status.FAILED, saved.getValue().getStatus());
        verifyNoInteractions(gateway);
    }
    @Test void unconfiguredTransportLeavesPendingAndSentJobCannotBeSentTwice() {
        PayrollEmail job = job();
        service.deliver(job.getId());
        verify(emails, never()).lockById(any()); verify(gateway, never()).send(any(), any(), any());
        when(gateway.ready()).thenReturn(true);
        service.deliver(job.getId());
        assertEquals(PayrollEmail.Status.SENT, job.getStatus()); assertNotNull(job.getSentAt()); assertEquals(1, job.getAttempts());
        service.deliver(job.getId());
        verify(gateway, times(1)).send(any(), any(), any());
    }
    @Test void transportFailureRetainsPaymentAndHasSanitizedErrorWithExplicitRetryOnly() {
        PayrollEmail job = job();
        when(gateway.ready()).thenReturn(true);
        doThrow(new IllegalStateException("SMTP secret-password")).when(gateway).send(any(), any(), any());
        service.deliver(job.getId());
        assertEquals(PayrollEmail.Status.FAILED, job.getStatus());
        assertEquals(PaidStatus.PAID, slip.getPaidStatus());
        assertFalse(job.getErrorMessage().contains("secret-password"));
        service.deliver(job.getId());
        verify(gateway, times(1)).send(any(), any(), any());
        when(emails.findByPayslipIdAndEventType(slip.getId(), PayrollEmail.EventType.PAYMENT)).thenReturn(Optional.of(job));
        service.retry(slip.getId());
        assertEquals(PayrollEmail.Status.PENDING, job.getStatus());
        job.setStatus(PayrollEmail.Status.SENT);
        assertThrows(IllegalStateException.class, () -> service.retry(slip.getId()));
    }
    @Test void gmailAddressIsPreferredForPayslipEmails() {
        Staff staff = new Staff();
        staff.setEmail("nhanvien.a@gmail.com"); staff.setCompanyEmail("a@congty.vn");
        assertEquals("nhanvien.a@gmail.com", PayrollEmailService.recipient(staff));
        staff.setEmail("ca.nhan@yahoo.com"); staff.setCompanyEmail("work.a@GMAIL.com");
        assertEquals("work.a@GMAIL.com", PayrollEmailService.recipient(staff));
        staff.setEmail("ca.nhan@yahoo.com"); staff.setCompanyEmail("a@congty.vn");
        assertEquals("a@congty.vn", PayrollEmailService.recipient(staff));
        staff.setCompanyEmail(null);
        assertEquals("ca.nhan@yahoo.com", PayrollEmailService.recipient(staff));
    }
    private PayrollEmail job() {
        PayrollEmail job = new PayrollEmail(); job.setId(UUID.randomUUID()); job.setPayslipId(slip.getId());
        job.setRecipient("work@example.test"); job.setSubject("Subject"); job.setContent("Body");
        when(emails.lockById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    @Test void confirmationAndPaymentHaveSeparateSnapshotsAndNeverClaimPaymentAtApproval() {
        slip.setNetSalary(13000000.0);
        slip.setPaidStatus(PaidStatus.UNPAID);
        service.enqueueConfirmation(slip);
        slip.setPaidStatus(PaidStatus.PAID);
        service.enqueuePayment(slip);
        var saved = org.mockito.ArgumentCaptor.forClass(PayrollEmail.class);
        verify(emails, times(2)).save(saved.capture());
        var confirmation = saved.getAllValues().get(0);
        var payment = saved.getAllValues().get(1);
        assertEquals(PayrollEmail.EventType.CONFIRMATION, confirmation.getEventType());
        assertTrue(confirmation.getContent().contains("chưa xác nhận đã trả tiền"));
        assertFalse(confirmation.getContent().contains("Số tiền đã thanh toán"));
        assertEquals(PayrollEmail.EventType.PAYMENT, payment.getEventType());
        assertTrue(payment.getContent().contains("Số tiền đã thanh toán"));
        when(emails.findByPayslipIdAndEventType(slip.getId(), PayrollEmail.EventType.CONFIRMATION)).thenReturn(Optional.of(confirmation));
        service.enqueueConfirmation(slip);
        verify(emails, times(2)).save(any());
    }
}
