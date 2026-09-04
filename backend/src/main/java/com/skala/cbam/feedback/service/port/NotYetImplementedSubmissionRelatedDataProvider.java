package com.skala.cbam.feedback.service.port;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** CBAM-90(제출 데이터)이 이 브랜치에 병합되기 전까지 쓰는 임시 구현. 항상 빈 값. */
@Component
class NotYetImplementedSubmissionRelatedDataProvider implements SubmissionRelatedDataProvider {

    @Override
    public Optional<SubmissionInfo> findSubmissionInfo(Long submissionId) {
        return Optional.empty();
    }

    @Override
    public List<SubmissionInfo> findDraftableSubmissions(YearMonth reportingMonth) {
        return List.of();
    }
}
