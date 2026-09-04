package com.skala.cbam.feedback.service.port;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 피드백 초안 생성(42·43번) 응답 중 <b>제출(submission) 도메인이 소유한</b> 값을 채우는 자리.
 *
 * <p>초안은 부적격·미제출 제출 건의 반려 사유를 근거로 만든다. CBAM-90(submission, PR #22)이 아직
 * 이 브랜치에 병합되지 않아 직접 참조할 수 없다 — Supplier/Part 도메인과 같은 이유, 같은 해법.
 *
 * <p><b>CBAM-90 이 병합되는 사람에게:</b> 이 인터페이스를 구현한 @Component 를 추가하고
 * {@link NotYetImplementedSubmissionRelatedDataProvider} 를 삭제하면 초안 생성이 그대로 실제
 * 반려 사유를 근거로 문안을 만든다.
 */
public interface SubmissionRelatedDataProvider {

    /** 특정 제출 건 하나 (42번 개별 생성, submissionIds 지정 시). */
    Optional<SubmissionInfo> findSubmissionInfo(Long submissionId);

    /** 이번 달 부적격·미제출 전체 (43번 일괄 생성, submissionIds·targets 둘 다 생략 시). */
    List<SubmissionInfo> findDraftableSubmissions(YearMonth reportingMonth);

    record SubmissionInfo(
            Long submissionId,
            Long supplierId,
            Long partSupplierId,
            String reportingMonth,
            boolean qualified,
            String rejectionReasonCode,
            String rejectionReason
    ) {
    }
}
