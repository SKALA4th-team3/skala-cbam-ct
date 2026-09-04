package com.skala.cbam.feedback.repository;

import com.skala.cbam.feedback.domain.Task;
import com.skala.cbam.feedback.domain.TaskType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findByFeedbackIdAndTypeOrderByAttemptNumberDesc(Long feedbackId, TaskType type);

    Optional<Task> findTopByFeedbackIdAndTypeOrderByAttemptNumberDesc(Long feedbackId, TaskType type);

    long countByFeedbackIdAndType(Long feedbackId, TaskType type);
}
