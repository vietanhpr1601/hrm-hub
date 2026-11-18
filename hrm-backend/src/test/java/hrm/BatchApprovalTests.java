package hrm;

import hrm.enums.LeaveApprovalStatus;
import hrm.enums.TimesheetStatus;
import hrm.model.LeaveRequest;
import hrm.model.Timesheet;
import hrm.repository.LeaveRequestRepository;
import hrm.repository.TimesheetRepository;
import hrm.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BatchApprovalTests {
    LeaveRequestService leaves = mock(LeaveRequestService.class);
    TimesheetService timesheets = mock(TimesheetService.class);
    LeaveRequestRepository leaveRepository = mock(LeaveRequestRepository.class);
    TimesheetRepository timesheetRepository = mock(TimesheetRepository.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    BatchApprovalService service;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        service = new BatchApprovalService(leaves, timesheets, leaveRepository, timesheetRepository, manager);
    }
    @Test void validatesBatchBeforeMutatingAnything() {
        UUID id = UUID.randomUUID();
        for (List<UUID> ids : List.of(Collections.<UUID>emptyList(), List.of(id, id), Collections.nCopies(501, id), Arrays.asList(id, null)))
            assertThrows(IllegalArgumentException.class, () -> service.approve(new BatchApprovalService.Request(ids), true));
        verifyNoInteractions(manager, leaves, timesheets);
    }
    @Test void onlyPendingLeaveIsApprovedAndOneFailureDoesNotStopOtherRows() {
        UUID approvedId = UUID.randomUUID(), staleId = UUID.randomUUID(), failedId = UUID.randomUUID();
        LeaveRequest pending = new LeaveRequest(); pending.setApprovalStatus(LeaveApprovalStatus.PENDING);
        LeaveRequest stale = new LeaveRequest(); stale.setApprovalStatus(LeaveApprovalStatus.REJECTED);
        when(leaveRepository.findForApproval(approvedId)).thenReturn(Optional.of(pending));
        when(leaveRepository.findForApproval(staleId)).thenReturn(Optional.of(stale));
        when(leaveRepository.findForApproval(failedId)).thenReturn(Optional.of(pending));
        when(leaves.approve(failedId, null)).thenThrow(new IllegalArgumentException("Không đủ phép năm"));
        var result = service.approve(new BatchApprovalService.Request(List.of(staleId, failedId, approvedId)), true);
        assertEquals(1, result.approved()); assertEquals(2, result.failed());
        assertTrue(result.items().get(1).message().contains("Không đủ phép năm"));
        verify(leaves, never()).approve(eq(staleId), any());
        verify(manager, times(2)).rollback(any()); verify(manager).commit(any());
    }
    @Test void submittedAttendanceCanBeApprovedButDraftAndDeletedRecordsCannot() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Timesheet submitted = new Timesheet(); submitted.setStatus(TimesheetStatus.SUBMITTED);
        Timesheet draft = new Timesheet(); draft.setStatus(TimesheetStatus.DRAFT);
        Timesheet deleted = new Timesheet(); deleted.setStatus(TimesheetStatus.SUBMITTED); deleted.setIsDeleted(true);
        when(timesheetRepository.findForApproval(ids.get(0))).thenReturn(Optional.of(submitted));
        when(timesheetRepository.findForApproval(ids.get(1))).thenReturn(Optional.of(draft));
        when(timesheetRepository.findForApproval(ids.get(2))).thenReturn(Optional.of(deleted));
        when(timesheets.approve(ids.get(0), TimesheetStatus.APPROVED, null)).thenReturn(true);
        var result = service.approve(new BatchApprovalService.Request(ids), false);
        assertEquals(1, result.approved()); assertEquals(2, result.failed());
        verify(timesheets, times(1)).approve(any(), any(), any());
    }
}
