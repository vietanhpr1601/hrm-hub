package hrm.service;

import hrm.enums.LeaveApprovalStatus;
import hrm.enums.TimesheetStatus;
import hrm.repository.LeaveRequestRepository;
import hrm.repository.TimesheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class BatchApprovalService {
    public record Request(List<UUID> ids) {}
    public record Item(UUID id, boolean success, String message) {}
    public record Result(int approved, int failed, List<Item> items) {}
    private final LeaveRequestService leaves;
    private final TimesheetService timesheets;
    private final LeaveRequestRepository leaveRepository;
    private final TimesheetRepository timesheetRepository;
    private final TransactionTemplate transaction;

    public BatchApprovalService(LeaveRequestService leaves, TimesheetService timesheets,
            LeaveRequestRepository leaveRepository, TimesheetRepository timesheetRepository,
            PlatformTransactionManager manager) {
        this.leaves = leaves; this.timesheets = timesheets;
        this.leaveRepository = leaveRepository; this.timesheetRepository = timesheetRepository;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result approve(Request request, boolean leave) {
        if (request == null || request.ids() == null || request.ids().isEmpty() || request.ids().size() > 500
                || request.ids().stream().anyMatch(Objects::isNull) || new HashSet<>(request.ids()).size() != request.ids().size())
            throw new IllegalArgumentException("Chọn từ 1 đến 500 bản ghi khác nhau để duyệt.");
        List<Item> results = new ArrayList<>();
        for (UUID id : request.ids()) {
            try {
                transaction.executeWithoutResult(status -> {
                    if (leave) {
                        var row = leaveRepository.findForApproval(id)
                                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nghỉ phép"));
                        if (Boolean.TRUE.equals(row.getIsDeleted()) || row.getApprovalStatus() != LeaveApprovalStatus.PENDING)
                            throw new IllegalStateException("Đơn không còn ở trạng thái chờ duyệt");
                        leaves.approve(id, null);
                    } else {
                        var row = timesheetRepository.findForApproval(id)
                                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ngày công"));
                        if (Boolean.TRUE.equals(row.getIsDeleted()) || row.getStatus() != TimesheetStatus.SUBMITTED)
                            throw new IllegalStateException("Ngày công không còn ở trạng thái chờ duyệt");
                        if (!timesheets.approve(id, TimesheetStatus.APPROVED, null))
                            throw new IllegalStateException("Không thể duyệt ngày công");
                    }
                });
                results.add(new Item(id, true, "Đã duyệt"));
            } catch (IllegalArgumentException | IllegalStateException error) {
                results.add(new Item(id, false, error.getMessage()));
            } catch (RuntimeException error) {
                results.add(new Item(id, false, "Không thể duyệt bản ghi. Hãy tải lại và kiểm tra."));
            }
        }
        int approved = (int) results.stream().filter(Item::success).count();
        return new Result(approved, results.size() - approved, List.copyOf(results));
    }
}
