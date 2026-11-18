package hrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hrm.dto.request.LeaveRequestDto;
import hrm.enums.LeaveApprovalStatus;
import hrm.enums.LeaveType;
import hrm.enums.NotificationType;
import hrm.enums.TimesheetStatus;
import hrm.model.LeaveRequest;
import hrm.model.Notification;
import hrm.model.Staff;
import hrm.model.Timesheet;
import hrm.model.User;
import hrm.repository.LeaveRequestRepository;
import hrm.repository.NotificationRepository;
import hrm.repository.StaffRepository;
import hrm.repository.TimesheetRepository;
import hrm.repository.UserRepository;
import hrm.security.JwtTokenProvider;
import hrm.security.PermissionPolicy;
import hrm.service.LeaveRequestService;
import hrm.service.TimesheetService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@HrmIntegrationTest
class NotificationIntegrationTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    @Autowired MockMvc mvc;
    @Autowired StaffRepository staffs;
    @Autowired UserRepository users;
    @Autowired TimesheetRepository sheets;
    @Autowired LeaveRequestRepository requests;
    @Autowired NotificationRepository notifications;
    @Autowired TimesheetService attendance;
    @Autowired LeaveRequestService leaves;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Value("${app.admin.username}") String adminUsername;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void manualAttendanceApprovalPersistsOneNotificationAndActualReapprovalAddsAnother() throws Exception {
        User employee = account(staff());
        Timesheet sheet = sheet(employee.getStaff(), DAY);
        approveAttendance(sheet.getId(), "APPROVED", 200);
        flush();
        Notification event = inbox(employee).get(0);
        assertEquals(NotificationType.ATTENDANCE_APPROVED, event.getType());
        assertEquals(sheet.getId(), event.getReferenceId());
        assertEquals(DAY, event.getReferenceDate());
        assertTrue(event.getMessage().contains("05/10/2026"));
        assertNotNull(event.getCreatedAt());
        assertNull(event.getReadAt());
        approveAttendance(sheet.getId(), "APPROVED", 200);
        assertEquals(1, inbox(employee).size());
        approveAttendance(sheet.getId(), "REJECTED", 200);
        assertEquals(1, inbox(employee).size());
        approveAttendance(sheet.getId(), "APPROVED", 200);
        flush();
        assertEquals(2, inbox(employee).size());
    }

    @Test
    void leaveApprovalCreatesOnlyLeaveNotificationDespiteAutomaticAttendanceApproval() throws Exception {
        User employee = account(staff());
        LeaveRequestDto leave = leave(employee.getStaff(), DAY);
        assertTrue(inbox(employee).isEmpty());
        approveLeave(leave.getId(), 200);
        flush();
        assertEquals(TimesheetStatus.APPROVED, sheets.findByStaffIdAndWorkingDate(employee.getStaff().getId(), DAY).orElseThrow().getStatus());
        Notification event = inbox(employee).get(0);
        assertEquals(NotificationType.LEAVE_APPROVED, event.getType());
        assertEquals(leave.getId(), event.getReferenceId());
        assertTrue(event.getMessage().contains("05/10/2026"));
        approveLeave(leave.getId(), 200);
        assertEquals(1, inbox(employee).size());
        leaves.reject(leave.getId(), "Thu hồi đơn để xét lại");
        assertEquals(1, inbox(employee).size());
        approveLeave(leave.getId(), 200);
        assertEquals(2, inbox(employee).size());
        assertTrue(inbox(employee).stream().allMatch(n -> n.getType() == NotificationType.LEAVE_APPROVED));
    }

    @Test
    void failedOrDeletedAttendanceApprovalDoesNotNotify() throws Exception {
        User employee = account(staff());
        Timesheet deleted = sheet(employee.getStaff(), DAY);
        deleted.setIsDeleted(true);
        flush();
        approveAttendance(deleted.getId(), "APPROVED", 400);
        approveAttendance(UUID.randomUUID(), "APPROVED", 400);
        assertTrue(inbox(employee).isEmpty());
        assertEquals(TimesheetStatus.SUBMITTED, sheets.findById(deleted.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectedLeaveApprovalValidationDoesNotChangeStatusOrNotify() throws Exception {
        User employee = account(staff());
        LeaveRequestDto leave = leave(employee.getStaff(), DAY);
        employee.getStaff().setAnnualLeave(0.0);
        flush();
        approveLeave(leave.getId(), 400);
        assertEquals(LeaveApprovalStatus.PENDING, requests.findById(leave.getId()).orElseThrow().getApprovalStatus());
        assertTrue(inbox(employee).isEmpty());
        assertTrue(sheets.findByStaffIdAndWorkingDate(employee.getStaff().getId(), DAY).isEmpty());
    }

    @Test
    void notificationsBelongOnlyToRecipientIncludingAdminAndReadStatePersists() throws Exception {
        User owner = account(staff());
        User other = account(staff());
        User admin = users.findByUsername(adminUsername).orElseThrow();
        attendance.approve(sheet(owner.getStaff(), DAY).getId(), TimesheetStatus.APPROVED, null);
        attendance.approve(sheet(owner.getStaff(), DAY.plusDays(1)).getId(), TimesheetStatus.APPROVED, null);
        attendance.approve(sheet(other.getStaff(), DAY).getId(), TimesheetStatus.APPROVED, null);
        flush();

        JsonNode firstPage = json(get("/api/notifications?pageIndex=1&pageSize=1"), owner, 200).path("data");
        assertEquals(2, firstPage.path("totalElements").asInt());
        assertEquals(1, firstPage.path("content").size());
        String firstId = firstPage.path("content").get(0).path("id").asText();
        String secondId = json(get("/api/notifications?pageIndex=2&pageSize=1"), owner, 200)
                .path("data").path("content").get(0).path("id").asText();
        assertNotEquals(firstId, secondId);
        assertEquals(2, unread(owner));
        assertEquals(1, unread(other));
        assertEquals(0, unread(admin));
        assertEquals(0, json(get("/api/notifications"), admin, 200).path("data").path("totalElements").asInt());
        json(put("/api/notifications/" + firstId + "/read"), other, 404);
        json(put("/api/notifications/" + firstId + "/read"), admin, 404);
        json(put("/api/notifications/" + UUID.randomUUID() + "/read"), owner, 404);

        JsonNode marked = json(put("/api/notifications/" + firstId + "/read"), owner, 200).path("data");
        String readAt = marked.path("readAt").asText();
        assertNotNull(Instant.parse(readAt));
        assertNotNull(Instant.parse(marked.path("createdAt").asText()));
        flush();
        assertEquals(Instant.parse(readAt), notifications.findById(UUID.fromString(firstId)).orElseThrow().getReadAt());
        assertEquals(readAt, json(put("/api/notifications/" + firstId + "/read"), owner, 200).path("data").path("readAt").asText());
        assertEquals(1, unread(owner));
        assertEquals(secondId, json(get("/api/notifications?unreadOnly=true"), owner, 200)
                .path("data").path("content").get(0).path("id").asText());
        json(put("/api/notifications/read-all"), owner, 200);
        flush();
        assertEquals(0, unread(owner));
        assertEquals(1, unread(other));
        assertEquals(2, json(get("/api/notifications"), owner, 200).path("data").path("totalElements").asInt());
        assertEquals(0, json(get("/api/notifications?unreadOnly=true"), owner, 200).path("data").path("totalElements").asInt());
    }

    @Test
    void accountWithNotificationGrantButWithoutStaffCanReadEmptyInboxAndValidatePaging() throws Exception {
        User account = account(null);
        assertTrue(account.getUserRoles().isEmpty());
        assertEquals(0, json(get("/api/notifications"), account, 200).path("data").path("totalElements").asInt());
        assertEquals(0, unread(account));
        json(put("/api/notifications/read-all"), account, 200);
        json(get("/api/notifications?pageIndex=0"), account, 400);
        json(get("/api/notifications?pageSize=101"), account, 400);
        json(get("/api/notifications?pageSize=0"), account, 400);
    }

    @Test
    void approvalHistoryIsCreatedWithoutViewPermissionAndAvailableAfterGrant() throws Exception {
        User employee = account(staff(), false);
        flush();
        assertFalse(PermissionPolicy.has(users.findById(employee.getId()).orElseThrow(), "NOTIFICATION_VIEW"));
        approveAttendance(sheet(employee.getStaff(), DAY).getId(), "APPROVED", 200);
        approveLeave(leave(employee.getStaff(), DAY.plusDays(1)).getId(), 200);
        flush();
        assertEquals(2, inbox(employee).size());
        assertFalse(PermissionPolicy.has(users.findById(employee.getId()).orElseThrow(), "NOTIFICATION_VIEW"));
        json(get("/api/notifications"), employee, 403);
        users.findById(employee.getId()).orElseThrow().getPermissionOverrides().put("NOTIFICATION_VIEW", "ALLOW");
        flush();
        assertEquals(2, unread(employee));
    }

    @Test
    void anonymousAndDisabledOrDeletedAccountsCannotReadOrChangeNotificationState() throws Exception {
        User account = account(staff());
        attendance.approve(sheet(account.getStaff(), DAY).getId(), TimesheetStatus.APPROVED, null);
        UUID id = inbox(account).get(0).getId();
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/notifications/" + id + "/read")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/notifications/read-all")).andExpect(status().isUnauthorized());

        String accessToken = tokens.generateAccessToken(account);
        account.setActive(false);
        flush();
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + accessToken)).andExpect(status().isUnauthorized());
        json(get("/api/notifications/unread-count"), account, 401);
        json(put("/api/notifications/" + id + "/read"), account, 401);
        json(put("/api/notifications/read-all"), account, 401);
        User persisted = users.findById(account.getId()).orElseThrow();
        persisted.setActive(true);
        persisted.setIsDeleted(true);
        flush();
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + accessToken)).andExpect(status().isUnauthorized());
        json(get("/api/notifications"), account, 401);
        assertNull(notifications.findById(id).orElseThrow().getReadAt());
    }

    @Test
    void lockedRecipientsReceiveHistoryButDeletedAccountsDoNot() throws Exception {
        User locked = account(staff());
        locked.setActive(false);
        User deleted = account(staff());
        deleted.setIsDeleted(true);
        attendance.approve(sheet(locked.getStaff(), DAY).getId(), TimesheetStatus.APPROVED, null);
        attendance.approve(sheet(deleted.getStaff(), DAY).getId(), TimesheetStatus.APPROVED, null);
        flush();
        assertEquals(1, inbox(locked).size());
        assertTrue(inbox(deleted).isEmpty());
        json(get("/api/notifications"), locked, 401);
        users.findById(locked.getId()).orElseThrow().setActive(true);
        flush();
        assertEquals(1, unread(locked));
    }

    @Test
    void historyStaysWithOriginalAccountAfterStaffUnlinkAndNewAccountStartsEmpty() throws Exception {
        Staff staff = staff();
        User original = account(staff);
        attendance.approve(sheet(staff, DAY).getId(), TimesheetStatus.APPROVED, null);
        UUID id = inbox(original).get(0).getId();
        original.setStaff(null);
        flush();
        User replacement = account(staffs.findById(staff.getId()).orElseThrow());
        assertEquals(1, unread(original));
        assertEquals(0, unread(replacement));
        json(put("/api/notifications/" + id + "/read"), replacement, 404);
        attendance.approve(sheet(staff, DAY.plusDays(1)).getId(), TimesheetStatus.APPROVED, null);
        flush();
        assertEquals(1, unread(original));
        assertEquals(1, unread(replacement));
        assertEquals(original.getId(), notifications.findById(id).orElseThrow().getRecipient().getId());
    }

    @Test
    void approvalForStaffWithoutAccountSucceedsWithoutNotifyingOtherAccounts() throws Exception {
        User unrelated = account(staff());
        Staff withoutAccount = staff();
        Timesheet sheet = sheet(withoutAccount, DAY);
        LeaveRequestDto leave = leave(withoutAccount, DAY.plusDays(1));
        approveAttendance(sheet.getId(), "APPROVED", 200);
        approveLeave(leave.getId(), 200);
        flush();
        assertEquals(TimesheetStatus.APPROVED, sheets.findById(sheet.getId()).orElseThrow().getStatus());
        assertEquals(LeaveApprovalStatus.APPROVED, requests.findById(leave.getId()).orElseThrow().getApprovalStatus());
        assertTrue(inbox(unrelated).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void approvalAndNotificationRollBackTogetherWhenTransactionFails() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> fixture());
        try {
            assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                attendance.approve(fixture.sheetId(), TimesheetStatus.APPROVED, null);
                leaves.approve(fixture.leaveId(), null);
                entityManager.flush();
                assertEquals(2, notifications.countByRecipientIdAndReadAtIsNull(fixture.userId()));
                throw new IllegalStateException("Mô phỏng giao dịch phê duyệt thất bại");
            }));
            tx.executeWithoutResult(status -> {
                assertEquals(TimesheetStatus.SUBMITTED, sheets.findById(fixture.sheetId()).orElseThrow().getStatus());
                assertEquals(LeaveApprovalStatus.PENDING, requests.findById(fixture.leaveId()).orElseThrow().getApprovalStatus());
                assertEquals(0, notifications.countByRecipientIdAndReadAtIsNull(fixture.userId()));
                assertTrue(sheets.findByStaffIdAndWorkingDate(fixture.staffId(), DAY.plusDays(1)).isEmpty());
            });
        } finally {
            cleanup(tx, fixture);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentApprovalsProduceOnlyOneNotificationPerActualTransition() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> fixture());
        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                await(start);
                attendance.approve(fixture.sheetId(), TimesheetStatus.APPROVED, null);
                leaves.approve(fixture.leaveId(), null);
            });
            var second = executor.submit(() -> {
                await(start);
                attendance.approve(fixture.sheetId(), TimesheetStatus.APPROVED, null);
                leaves.approve(fixture.leaveId(), null);
            });
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                List<Notification> events = notifications.findByRecipientId(fixture.userId(), PageRequest.of(0, 10)).getContent();
                assertEquals(2, events.size());
                assertEquals(1, events.stream().filter(n -> n.getType() == NotificationType.ATTENDANCE_APPROVED).count());
                assertEquals(1, events.stream().filter(n -> n.getType() == NotificationType.LEAVE_APPROVED).count());
            });
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            cleanup(tx, fixture);
        }
    }

    private Staff staff() {
        Staff staff = new Staff();
        staff.setStaffCode("NOTIFY_" + UUID.randomUUID());
        staff.setDisplayName("Nhân viên kiểm thử thông báo");
        staff.setAnnualLeave(12.0);
        return staffs.saveAndFlush(staff);
    }

    private User account(Staff staff) {
        return account(staff, true);
    }

    private User account(Staff staff, boolean notificationGrant) {
        User account = new User();
        account.setUsername("notification-test-" + UUID.randomUUID());
        account.setPassword("unused-test-hash");
        account.setStaff(staff);
        account.setActive(true);
        if (notificationGrant) account.getPermissionOverrides().put("NOTIFICATION_VIEW", "ALLOW");
        return users.saveAndFlush(account);
    }

    private Timesheet sheet(Staff staff, LocalDate date) {
        Timesheet sheet = new Timesheet();
        sheet.setStaff(staff);
        sheet.setWorkingDate(date);
        sheet.setStatus(TimesheetStatus.SUBMITTED);
        sheet.setTotalWorkRatio(1.0);
        sheet.setStandardHours(8.0);
        return sheets.saveAndFlush(sheet);
    }

    private LeaveRequestDto leave(Staff staff, LocalDate date) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setRequestStaffId(staff.getId());
        dto.setFromDate(date);
        dto.setToDate(date);
        dto.setLeaveType(LeaveType.ANNUAL);
        dto.setRequestReason("Kiểm thử thông báo cá nhân");
        return leaves.create(dto);
    }

    private List<Notification> inbox(User owner) {
        return notifications.findByRecipientId(owner.getId(), PageRequest.of(0, 100)).getContent();
    }

    private long unread(User owner) throws Exception {
        return json(get("/api/notifications/unread-count"), owner, 200).path("data").path("unreadCount").asLong();
    }

    private JsonNode json(MockHttpServletRequestBuilder request, User owner, int expectedStatus) throws Exception {
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();
        return mapper.readTree(mvc.perform(request.header("Authorization", "Bearer " + tokens.generateAccessToken(owner)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsByteArray());
    }

    private void approveAttendance(UUID id, String status, int expectedStatus) throws Exception {
        mvc.perform(put("/api/timesheets/" + id + "/approve").param("status", status)
                        .with(user(adminUsername).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().is(expectedStatus));
    }

    private void approveLeave(UUID id, int expectedStatus) throws Exception {
        mvc.perform(put("/api/leave-requests/" + id + "/approve").with(user(adminUsername).authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().is(expectedStatus));
    }

    private void flush() { entityManager.flush(); entityManager.clear(); }

    private Fixture fixture() {
        Staff staff = staff();
        User user = account(staff);
        Timesheet sheet = sheet(staff, DAY);
        LeaveRequestDto leave = leave(staff, DAY.plusDays(1));
        return new Fixture(staff.getId(), user.getId(), sheet.getId(), leave.getId());
    }

    private void cleanup(TransactionTemplate tx, Fixture fixture) {
        tx.executeWithoutResult(status -> {
            notifications.deleteAll(notifications.findByRecipientId(fixture.userId(), PageRequest.of(0, 100)));
            sheets.deleteAll(sheets.findByStaffIdAndWorkingDateBetweenOrderByWorkingDateAsc(fixture.staffId(), DAY, DAY.plusDays(1)));
            requests.deleteById(fixture.leaveId());
            users.deleteById(fixture.userId());
            staffs.deleteById(fixture.staffId());
        });
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private record Fixture(UUID staffId, UUID userId, UUID sheetId, UUID leaveId) {}
}
