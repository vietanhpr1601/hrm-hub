package hrm.service;

import hrm.enums.ProjectRole;
import hrm.model.Project;
import hrm.model.Staff;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectAssignmentMailerTests {
    private Project project() {
        Project project = new Project();
        project.setName("Website bán hàng"); project.setCode("WEB01"); project.setCompanyName("Công ty Minh Phát");
        project.setStartDate(LocalDate.of(2026, 11, 1)); project.setEndDate(LocalDate.of(2026, 12, 31));
        return project;
    }
    private Staff staff(String email) {
        Staff staff = new Staff(); staff.setDisplayName("Nguyễn Văn A"); staff.setStaffCode("NV01"); staff.setEmail(email);
        return staff;
    }

    @Test void emailTellsWhichProjectCompanyAndRole() {
        String role = ProjectAssignmentMailer.roleText(ProjectRole.MEMBER, "Lập trình viên");
        assertEquals("Lập trình viên (Người thực hiện)", role);
        assertEquals("Quản lý dự án", ProjectAssignmentMailer.roleText(ProjectRole.MANAGER, " "));
        String body = ProjectAssignmentMailer.content(project(), staff("a@gmail.com"), role, "Trần B");
        assertTrue(body.contains("Website bán hàng (WEB01)"));
        assertTrue(body.contains("Công ty Minh Phát"));
        assertTrue(body.contains("Vai trò của bạn: Lập trình viên (Người thực hiện)"));
        assertTrue(body.contains("01/11/2026 - 31/12/2026"));
        assertTrue(body.contains("Người phân công: Trần B"));
        assertEquals("HRM - Bạn được phân công vào dự án: Website bán hàng", ProjectAssignmentMailer.subject(project()));
    }

    @Test void sendsToGmailWhenMailIsConfiguredAndSkipsOtherwise() {
        PayrollMailGateway gateway = mock(PayrollMailGateway.class);
        when(gateway.ready()).thenReturn(true);
        ProjectAssignmentMailer mailer = new ProjectAssignmentMailer(gateway);
        Staff staff = staff("nguyenvana@gmail.com"); staff.setCompanyEmail("a@congty.vn");
        mailer.notifyAssigned(project(), staff, ProjectRole.MANAGER, "Trưởng nhóm", "Admin");
        verify(gateway, timeout(3000)).send(eq("nguyenvana@gmail.com"), contains("Website bán hàng"), contains("Trưởng nhóm (Quản lý dự án)"));

        mailer.notifyAssigned(project(), staff("không-phải-email"), ProjectRole.MEMBER, null, null);
        when(gateway.ready()).thenReturn(false);
        mailer.notifyAssigned(project(), staff("b@gmail.com"), ProjectRole.MEMBER, null, null);
        verify(gateway, after(500).times(1)).send(any(), any(), any());
    }
}
