package hrm;

import hrm.enums.TimesheetStatus;
import hrm.model.Staff;
import hrm.model.Timesheet;
import hrm.repository.StaffRepository;
import hrm.repository.TimesheetRepository;
import hrm.service.BatchApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@HrmIntegrationTest
class BatchApprovalTransactionTests {
    @Autowired BatchApprovalService batch;
    @Autowired StaffRepository staffs;
    @Autowired TimesheetRepository timesheets;
    @Autowired PlatformTransactionManager manager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void commitsValidRowsIndependentlyAndDoesNotReapproveStaleRecords() {
        var tx = new TransactionTemplate(manager);
        List<UUID> fixture = tx.execute(status -> {
            Staff staff = new Staff(); staff.setStaffCode("BATCH_" + UUID.randomUUID()); staff.setDisplayName("Batch test");
            staffs.saveAndFlush(staff);
            List<UUID> ids = new ArrayList<>(); ids.add(staff.getId());
            for (int i = 0; i < 2; i++) {
                Timesheet row = new Timesheet(); row.setStaff(staff); row.setWorkingDate(LocalDate.of(2030, 1, 7).plusDays(i));
                row.setStatus(i == 0 ? TimesheetStatus.SUBMITTED : TimesheetStatus.DRAFT);
                timesheets.saveAndFlush(row); ids.add(row.getId());
            }
            return ids;
        });
        try {
            var result = batch.approve(new BatchApprovalService.Request(List.of(fixture.get(2), fixture.get(1))), false);
            assertEquals(1, result.approved()); assertEquals(1, result.failed());
            tx.executeWithoutResult(status -> {
                assertEquals(TimesheetStatus.APPROVED, timesheets.findById(fixture.get(1)).orElseThrow().getStatus());
                assertEquals(TimesheetStatus.DRAFT, timesheets.findById(fixture.get(2)).orElseThrow().getStatus());
            });
            var repeated = batch.approve(new BatchApprovalService.Request(List.of(fixture.get(1))), false);
            assertEquals(0, repeated.approved()); assertEquals(1, repeated.failed());
        } finally {
            tx.executeWithoutResult(status -> {
                timesheets.deleteAllById(fixture.subList(1, 3)); timesheets.flush();
                staffs.deleteById(fixture.get(0));
            });
        }
    }
}
