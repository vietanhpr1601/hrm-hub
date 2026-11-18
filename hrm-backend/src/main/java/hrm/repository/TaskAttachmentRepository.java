package hrm.repository;

import hrm.model.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {
    List<TaskAttachment> findByTaskIdAndIsDeletedFalse(UUID taskId);
}
