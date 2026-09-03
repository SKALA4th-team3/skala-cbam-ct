package com.skala.cbam.submission.repository;

import com.skala.cbam.submission.domain.UnregisteredPart;
import com.skala.cbam.submission.domain.UnregisteredPartStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnregisteredPartRepository extends JpaRepository<UnregisteredPart, Long> {

    List<UnregisteredPart> findBySubmissionId(Long submissionId);

    /** 데이터 확정(31번)이 막는 조건 — OPEN 상태 미등록 부품이 하나라도 있으면 확정 불가. */
    List<UnregisteredPart> findBySubmissionIdAndStatus(Long submissionId, UnregisteredPartStatus status);
}
