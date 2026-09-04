package com.skala.cbam.submission.repository;

import com.skala.cbam.submission.domain.Alert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findBySubmissionIdOrderByRuleIdAscCheckIdAsc(Long submissionId);
}
