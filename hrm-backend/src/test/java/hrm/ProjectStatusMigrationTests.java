package hrm;

import hrm.config.ProjectStatusMigration;
import hrm.model.Project;
import hrm.model.ProjectWorkingStatus;
import hrm.model.Task;
import hrm.repository.ProjectWorkingStatusRepository;
import hrm.repository.TaskRepository;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStatusMigrationTests {
    private final ProjectWorkingStatusRepository repo = mock(ProjectWorkingStatusRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);

    private List<ProjectWorkingStatus> preset(Project project, String[][] spec) {
        List<ProjectWorkingStatus> list = new ArrayList<>();
        for (int i = 0; i < spec.length; i++) {
            ProjectWorkingStatus ws = new ProjectWorkingStatus();
            ws.setId(UUID.randomUUID()); ws.setProject(project); ws.setName(spec[i][0]); ws.setCode(spec[i][1]);
            ws.setDisplayOrder(i + 1); ws.setIsDeleted(false);
            list.add(ws);
        }
        return list;
    }
    private Task task(List<ProjectWorkingStatus> list, String code) {
        Task task = new Task();
        task.setStatus(list.stream().filter(ws -> ws.getCode().equals(code)).findFirst().orElseThrow());
        return task;
    }
    private List<String> active(List<ProjectWorkingStatus> list) {
        return list.stream().filter(ws -> !ws.getIsDeleted()).sorted(Comparator.comparing(ProjectWorkingStatus::getDisplayOrder))
                .map(ProjectWorkingStatus::getName).toList();
    }

    @Test void oldEnglishAndFourStepPresetsBecomeThreeSteps() {
        Project english = new Project(); english.setId(UUID.randomUUID());
        Project fourStep = new Project(); fourStep.setId(UUID.randomUUID());
        List<ProjectWorkingStatus> a = preset(english, new String[][] { { "Todo", "TODO" }, { "Doing", "DOING" }, { "Resolved", "RESOLVED" },
                { "Deployed", "DEPLOYED" }, { "Tested", "TESTED" }, { "Feedback", "FEEDBACK" }, { "Released", "RELEASED" }, { "Completed", "COMPLETED" } });
        List<ProjectWorkingStatus> b = preset(fourStep, new String[][] { { "Cần làm", "TODO" }, { "Đang làm", "DOING" },
                { "Chờ kiểm tra", "REVIEW" }, { "Hoàn thành", "DONE" } });
        Task todo = task(a, "TODO"), tested = task(a, "TESTED"), released = task(a, "RELEASED"), review = task(b, "REVIEW"), done = task(b, "DONE");
        List<ProjectWorkingStatus> all = new ArrayList<>(a); all.addAll(b);
        when(repo.findAll()).thenReturn(all);
        when(tasks.findByProjectId(english.getId())).thenReturn(List.of(todo, tested, released));
        when(tasks.findByProjectId(fourStep.getId())).thenReturn(List.of(review, done));

        new ProjectStatusMigration(repo, tasks).run(null);

        assertEquals(List.of("Chưa làm", "Đang làm", "Hoàn thành"), active(a));
        assertEquals(List.of("Chưa làm", "Đang làm", "Hoàn thành"), active(b));
        assertEquals("Chưa làm", todo.getStatus().getName());
        assertEquals("Đang làm", tested.getStatus().getName());
        assertEquals("Hoàn thành", released.getStatus().getName());
        assertEquals("Đang làm", review.getStatus().getName());
        assertEquals("Hoàn thành", done.getStatus().getName());

        // Chạy lại không đổi gì; bộ cột tự đặt không bị đụng tới.
        new ProjectStatusMigration(repo, tasks).run(null);
        assertEquals(List.of("Chưa làm", "Đang làm", "Hoàn thành"), active(a));
        List<ProjectWorkingStatus> custom = preset(english, new String[][] { { "Việc của tôi", "MINE" } });
        when(repo.findAll()).thenReturn(custom);
        new ProjectStatusMigration(repo, tasks).run(null);
        assertEquals("Việc của tôi", custom.get(0).getName());
    }
}
