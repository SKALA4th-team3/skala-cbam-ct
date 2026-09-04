package com.skala.cbam.submission.repository;

import com.skala.cbam.submission.domain.ExtractionField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractionFieldRepository extends JpaRepository<ExtractionField, Long> {

    List<ExtractionField> findBySubmissionIdOrderByFieldCodeAscSequenceNumberAsc(Long submissionId);
}
