package hrm.config;

import hrm.model.ProjectWorkingStatus;
import hrm.model.Task;
import hrm.repository.ProjectWorkingStatusRepository;
import hrm.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Đưa các bộ cột Kanban mặc định cũ về quy trình 3 bước: Chưa làm → Đang làm → Hoàn thành.
 * Áp dụng cho bộ 8 cột tiếng Anh (Todo … Completed) và bộ 4 bước (Cần làm, Đang làm, Chờ kiểm tra, Hoàn thành),
 * chỉ khi dự án còn dùng nguyên bộ mặc định (tên và mã chưa bị sửa). Công việc ở cột bị gộp được chuyển sang cột tương ứng.
 * Chạy nhiều lần không thay đổi gì thêm.
 */
@Component
public class ProjectStatusMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProjectStatusMigration.class);

    /** Một bộ mặc định cũ: mã → tên, cột giữ lại (mã cũ → mã mới) và cột bị gộp (mã cũ → mã mới). */
    private record Preset(Map<String, String> names, Map<String, String> keep, Map<String, String> merge) {}

    private static final List<Preset> PRESETS = List.of(
            new Preset(Map.of("TODO", "Todo", "DOING", "Doing", "RESOLVED", "Resolved", "DEPLOYED", "Deployed",
                    "TESTED", "Tested", "FEEDBACK", "Feedback", "RELEASED", "Released", "COMPLETED", "Completed"),
                    Map.of("TODO", "TODO", "DOING", "DOING", "COMPLETED", "DONE"),
                    Map.of("RESOLVED", "DOING", "DEPLOYED", "DOING", "TESTED", "DOING", "FEEDBACK", "DOING", "RELEASED", "DONE")),
            new Preset(Map.of("TODO", "Cần làm", "DOING", "Đang làm", "REVIEW", "Chờ kiểm tra", "DONE", "Hoàn thành"),
                    Map.of("TODO", "TODO", "DOING", "DOING", "DONE", "DONE"),
                    Map.of("REVIEW", "DOING")));

    /** Cột mới: mã → {tên, màu, thứ tự}. */
    private static final Map<String, String[]> TARGET = Map.of(
            "TODO", new String[] { "Chưa làm", "#757575", "1" },
            "DOING", new String[] { "Đang làm", "#ed6c02", "2" },
            "DONE", new String[] { "Hoàn thành", "#2e7d32", "3" });

    private final ProjectWorkingStatusRepository statuses;
    private final TaskRepository tasks;

    public ProjectStatusMigration(ProjectWorkingStatusRepository statuses, TaskRepository tasks) {
        this.statuses = statuses;
        this.tasks = tasks;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<UUID, List<ProjectWorkingStatus>> byProject = statuses.findAll().stream()
                .filter(ws -> ws.getProject() != null && !Boolean.TRUE.equals(ws.getIsDeleted()))
                .collect(Collectors.groupingBy(ws -> ws.getProject().getId()));
        int migrated = 0;
        for (Map.Entry<UUID, List<ProjectWorkingStatus>> entry : byProject.entrySet()) {
            List<ProjectWorkingStatus> list = entry.getValue();
            Preset preset = PRESETS.stream().filter(p -> matches(list, p.names())).findFirst().orElse(null);
            if (preset == null) continue;
            Map<String, ProjectWorkingStatus> byOldCode = list.stream().collect(Collectors.toMap(ProjectWorkingStatus::getCode, ws -> ws));
            Map<String, ProjectWorkingStatus> target = new HashMap<>();
            preset.keep().forEach((oldCode, newCode) -> target.put(newCode, byOldCode.get(oldCode)));
            // Chuyển công việc ở cột bị gộp (dựa trên mã cũ, trước khi đổi tên).
            Map<UUID, String> mergeById = new HashMap<>();
            preset.merge().forEach((oldCode, newCode) -> mergeById.put(byOldCode.get(oldCode).getId(), newCode));
            for (Task task : tasks.findByProjectId(entry.getKey())) {
                if (task.getStatus() == null) continue;
                String newCode = mergeById.get(task.getStatus().getId());
                if (newCode != null) task.setStatus(target.get(newCode));
            }
            preset.merge().keySet().forEach(oldCode -> byOldCode.get(oldCode).setIsDeleted(true));
            target.forEach((code, ws) -> {
                String[] spec = TARGET.get(code);
                ws.setCode(code); ws.setName(spec[0]); ws.setColor(spec[1]); ws.setDisplayOrder(Integer.parseInt(spec[2]));
            });
            migrated++;
        }
        if (migrated > 0) log.info("Đã chuyển {} dự án sang quy trình 3 bước: Chưa làm → Đang làm → Hoàn thành", migrated);
    }

    private static boolean matches(List<ProjectWorkingStatus> list, Map<String, String> names) {
        return list.size() == names.size() && list.stream()
                .allMatch(ws -> ws.getCode() != null && ws.getName() != null && ws.getName().equals(names.get(ws.getCode())));
    }
}
