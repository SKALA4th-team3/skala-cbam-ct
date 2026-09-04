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

    /**
     * 초안의 <b>근거</b>. AI 초안(42~45번)은 여기 담긴 것만 협력사에 요청할 수 있다 —
     * 근거 밖 항목을 요구하면 서버가 그 초안을 버리고 기본 템플릿으로 되돌린다(46번).
     *
     * <p>CBAM-90 이 붙기 전에는 뒤쪽 다섯이 비어 있고, 그러면 초안은 기본 템플릿으로 간다.
     * 없는 근거로 문장을 지어내지 않기 위해서다.
     */
    record SubmissionInfo(
            Long submissionId,
            Long supplierId,
            Long partSupplierId,
            String reportingMonth,
            boolean qualified,
            String rejectionReasonCode,
            String rejectionReason,
            /** 37번 판정 결과 — 적격·부적격·조건부. */
            String judgement,
            /** 판정 규칙 코드 R1~R7 과 그 이름 (№27 judgementReasons). */
            String ruleId,
            String ruleName,
            /** 23~24번에서 확인되지 않은 항목. 안내문이 이것만 요청한다. */
            List<MissingField> missingFields,
            /** 25번 미등록 부품의 원문 표기. */
            List<String> unregisteredPartNames
    ) {

        public SubmissionInfo {
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
            unregisteredPartNames = unregisteredPartNames == null ? List.of() : List.copyOf(unregisteredPartNames);
        }

        /** 근거를 아직 채울 수 없는 구현이 쓰는 짧은 생성자 (CBAM-90 병합 전). */
        public SubmissionInfo(Long submissionId, Long supplierId, Long partSupplierId, String reportingMonth,
                              boolean qualified, String rejectionReasonCode, String rejectionReason) {
            this(submissionId, supplierId, partSupplierId, reportingMonth, qualified,
                    rejectionReasonCode, rejectionReason, null, null, null, List.of(), List.of());
        }

        /**
         * @param rawValue 원문 표기. 비어 있으면 「원문에 값이 없다」(R2), 값이 있으면
         *                 「값은 있는데 표준 단위로 못 옮겼다」(R5) — 안내문 문장이 달라진다
         */
        public record MissingField(String key, String label, String rawValue, String note) {
        }
    }
}
