package com.skala.cbam.mail.service.port;

import com.skala.cbam.ai.dto.ExtractionResult;
import java.util.List;

/**
 * 22~25번 분석 결과를 저장하는 자리 (CBAM-100).
 *
 * <p>추출 결과가 실제로 들어가는 곳은 <b>제출(submission) 도메인</b>이다 — {@code submission} ·
 * {@code extraction_field} · {@code unregistered_part} 테이블이 거기 있다. CBAM-90(PR #22)이 아직
 * 병합되지 않아 직접 참조할 수 없다. Supplier·Part 때와 같은 이유, 같은 해법이다.
 *
 * <p><b>CBAM-90 을 병합하는 사람에게:</b> 이 인터페이스를 구현한 {@code @Component} 를 추가하고
 * {@link NotYetImplementedAnalysisResultSink} 를 지우면 분석 결과가 그대로 저장된다.
 * 매핑은 이렇다 — 추출 항목 하나가 {@code ExtractionField} 한 행이 된다.
 *
 * <table border="1">
 *   <caption>추출 결과 → PR #22 의 엔티티</caption>
 *   <tr><th>{@link ExtractionResult.Item}</th><th>{@code ExtractionField}</th></tr>
 *   <tr><td>{@code key}</td><td>{@code fieldCode}</td></tr>
 *   <tr><td>{@code value} (숫자)</td><td>{@code normalizedDecimal}</td></tr>
 *   <tr><td>{@code value} (문자열)</td><td>{@code normalizedText} · {@code normalizedCountryCode}</td></tr>
 *   <tr><td>{@code rawValue}</td><td>{@code rawValue}</td></tr>
 *   <tr><td>{@code unit}</td><td>{@code unit}</td></tr>
 *   <tr><td>{@code emissionScope}</td><td>{@code emissionScope}</td></tr>
 *   <tr><td>{@code conversionFailReason}</td><td>{@code conversionFailureReason}</td></tr>
 *   <tr><td>{@code source.attachmentId}</td><td>{@code sourceAttachmentId}</td></tr>
 *   <tr><td>{@code source.locator}</td><td>{@code sourceLocator}</td></tr>
 * </table>
 *
 * <p>{@code confidence} 를 받을 컬럼이 PR #22 에 없다 — 저장할지 버릴지는 팀이 정한다.
 * 화면은 0.9 미만을 「사람 확인 대기」로 표시하므로 버리면 그 표시가 사라진다.
 *
 * <p><b>적격 여부(R3)는 여기서 정하지 않는다.</b> 33~37번은 규칙이지 AI 가 아니다(ADR-0010 ①).
 * 이 sink 는 읽어낸 값을 저장할 뿐이고, {@code eligibilityStatus} 판정은 그 규칙이 한다.
 */
public interface AnalysisResultSink {

    /**
     * @param mailReceiptId 이 결과가 나온 접수 메일
     * @param result 서버가 이미 정합성을 맞춘 추출 결과 ({@link ExtractionResult#normalize()} 를 지난 것)
     * @return 만들어진 제출 데이터와 미등록 부품 수. №19 의 resourceIds·unregisteredPartCount 가 된다
     */
    Outcome save(Long mailReceiptId, ExtractionResult result);

    record Outcome(List<Long> submissionIds, int unregisteredPartCount) {

        public Outcome {
            submissionIds = submissionIds == null ? List.of() : List.copyOf(submissionIds);
        }

        public static Outcome empty() {
            return new Outcome(List.of(), 0);
        }
    }
}
