package com.skala.cbam.feedback.repository;

import com.skala.cbam.feedback.domain.FeedbackDraft;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackDraftRepository extends JpaRepository<FeedbackDraft, Long> {

    List<FeedbackDraft> findByFeedbackIdOrderByVersionNumberDesc(Long feedbackId);

    Optional<FeedbackDraft> findByFeedbackIdAndVersionNumber(Long feedbackId, Short versionNumber);

    Optional<FeedbackDraft> findTopByFeedbackIdOrderByVersionNumberDesc(Long feedbackId);

    long countByFeedbackId(Long feedbackId);
}
