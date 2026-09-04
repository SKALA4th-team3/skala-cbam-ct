package com.skala.cbam.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.cbam.ai.dto.DraftInput;
import com.skala.cbam.ai.dto.DraftResult;
import com.skala.cbam.ai.dto.ExtractionResult;
import com.skala.cbam.ai.prompt.DraftStyle;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>모델이 실제로 어긴 것들을 서버가 막는지 확인한다.</b>
 *
 * <p>여기 있는 사례는 상상한 것이 아니다 — {@code docs/product/prompts/} 의 프롬프트로 OpenAI 를
 * 실제로 불러서 나온 응답들이다. 프롬프트에 두 번 적어도 모델이 지키지 않는 것이 있어
 * 서버가 강제하기로 했다(ADR-0012).
 */
class AiResponseGuardTest {

    // ── 22~25번 추출 응답 정합성 ─────────────────────────────────

    @Test
    @DisplayName("단위를 몰라 변환에 실패했는데 value 가 남아 있으면 비운다 — 단위 없는 배출량을 저장하지 않는다")
    void 변환_실패인데_값이_남아_있으면_비운다() {
        // 실제 응답: rawValue "45,000" · conversionFailReason UNIT_NOT_RECOGNIZED · value 45000
        // 프롬프트가 "value 와 unit 을 둘 다 null 로" 라고 두 번 말해도 모델이 값을 남긴다
        ExtractionResult raw = analyzed(item("fuel_lng", "LNG", "45,000", 45000, null,
                "UNIT_NOT_RECOGNIZED", "단위가 원문에 없습니다", 0.9));

        ExtractionResult.Normalized normalized = raw.normalize();

        ExtractionResult.Item fixed = normalized.result().items().get(0);
        assertThat(fixed.value()).isNull();
        assertThat(fixed.unit()).isNull();
        // 원문은 지우지 않는다 — 담당자가 원문과 표준값을 나란히 본다 (24번)
        assertThat(fixed.rawValue()).isEqualTo("45,000");
        assertThat(normalized.repairs()).anyMatch(r -> r.contains("fuel_lng"));
    }

    @Test
    @DisplayName("변환에 성공한 값은 건드리지 않는다")
    void 정상_변환된_값은_그대로_둔다() {
        ExtractionResult raw = analyzed(item("electricity", "전력", "480,000 kWh", 480, "MWh",
                null, "환산 kWh→MWh", 1.0));

        ExtractionResult.Normalized normalized = raw.normalize();

        ExtractionResult.Item kept = normalized.result().items().get(0);
        assertThat(kept.value()).isEqualTo(480);
        assertThat(kept.unit()).isEqualTo("MWh");
        assertThat(normalized.repairs()).isEmpty();
    }

    @Test
    @DisplayName("분석 실패인데 배열에 값이 있으면 비운다 — 준 부품 목록을 결과로 베끼는 응답을 봤다")
    void 분석_실패면_결과_배열을_비운다() {
        ExtractionResult raw = new ExtractionResult(
                ExtractionResult.ANALYSIS_FAILED, "PARSE_FAILED", "unknown",
                List.of(item("production", "생산량", "900", 900, "TON", null, "읽음", 1.0)),
                List.of(new ExtractionResult.UnregisteredPart("열연강판", "입력으로 준 목록을 베낀 것")));

        ExtractionResult.Normalized normalized = raw.normalize();

        assertThat(normalized.result().items()).isEmpty();
        assertThat(normalized.result().unregisteredParts()).isEmpty();
        assertThat(normalized.result().failureReason()).isEqualTo("PARSE_FAILED");
        assertThat(normalized.repairs()).isNotEmpty();
    }

    @Test
    @DisplayName("note 가 비면 서버가 채운다 — 24번 「사유를 남긴다」가 빈 채로 저장되지 않는다")
    void 빈_note_를_채운다() {
        ExtractionResult raw = analyzed(item("production", "생산량", "1,250 t", 1250, "TON", null, "", 1.0));

        ExtractionResult.Normalized normalized = raw.normalize();

        assertThat(normalized.result().items().get(0).note()).isNotBlank();
        assertThat(normalized.repairs()).anyMatch(r -> r.contains("note"));
    }

    @Test
    @DisplayName("같은 미등록 부품이 여러 번 오면 하나만 남긴다 — 담당자가 같은 것을 두 번 등록하지 않게")
    void 미등록_부품_중복을_지운다() {
        ExtractionResult raw = new ExtractionResult(ExtractionResult.ANALYZED, null, "ko", List.of(),
                List.of(new ExtractionResult.UnregisteredPart("아연도금강판", "목록에 없음"),
                        new ExtractionResult.UnregisteredPart(" 아연도금강판 ", "다시 나옴")));

        ExtractionResult.Normalized normalized = raw.normalize();

        assertThat(normalized.result().unregisteredParts()).hasSize(1);
        assertThat(normalized.result().unregisteredParts().get(0).rawPartName()).isEqualTo("아연도금강판");
    }

    // ── 46번 세 번째 실패: 근거 밖 요구 ───────────────────────────

    @Test
    @DisplayName("근거에 없는 항목을 요구하면 잡아낸다 — 스키마로는 막히지 않는 실패다")
    void 근거_밖_요구를_잡아낸다() {
        DraftInput basis = draftInputWith("electricity", "fuel_lng");
        DraftResult draft = draftRequesting("electricity", "waterUsage", "wasteEmission");

        List<String> unsupported = draft.unsupportedRequests(basis.allowedRequestKeys());

        assertThat(unsupported).containsExactly("waterUsage", "wasteEmission");
    }

    @Test
    @DisplayName("근거 안의 항목만 요구하면 통과시킨다")
    void 근거_안의_요구는_통과한다() {
        DraftInput basis = draftInputWith("electricity", "fuel_lng");
        DraftResult draft = draftRequesting("electricity", "fuel_lng");

        assertThat(draft.unsupportedRequests(basis.allowedRequestKeys())).isEmpty();
    }

    @Test
    @DisplayName("미등록 부품의 원문 표기도 요구해도 되는 항목이다 (25번)")
    void 미등록_부품은_요구할_수_있다() {
        DraftInput basis = new DraftInput("성진스틸", "2026-08", null, "부적격", "R2", "필수 항목 누락",
                "직접 배출량을 확인할 수 없습니다",
                List.of(new DraftInput.MissingItem("electricity", "전력", "", "기재 없음")),
                List.of("아연도금강판"), null, DraftStyle.FORMAL, null);

        DraftResult draft = draftRequesting("아연도금강판");

        assertThat(draft.unsupportedRequests(basis.allowedRequestKeys())).isEmpty();
    }

    @Test
    @DisplayName("제목이나 본문이 비면 쓸 수 없는 초안이다 — 빈 메일이 확정 화면으로 가지 않는다")
    void 빈_초안은_쓰지_않는다() {
        assertThat(new DraftResult("", List.of("본문"), List.of(), List.of(), null).isUsable()).isFalse();
        assertThat(new DraftResult("제목", List.of(), List.of(), List.of(), null).isUsable()).isFalse();
        assertThat(new DraftResult("제목", List.of("   "), List.of(), List.of(), null).isUsable()).isFalse();
        assertThat(new DraftResult("제목", List.of("본문"), List.of(), List.of(), null).isUsable()).isTrue();
    }

    @Test
    @DisplayName("№27 의 body 는 문자열 하나다 — 문단 배열을 서버가 이어 붙인다")
    void 문단_배열을_한_문자열로_잇는다() {
        DraftResult draft = new DraftResult("제목", List.of("인사", "상황", "맺음"), List.of(), List.of(), null);

        assertThat(draft.bodyText()).isEqualTo("인사\n\n상황\n\n맺음");
    }

    @Test
    @DisplayName("근거가 하나도 없으면 AI 를 부르지 않는다 — 없는 근거로 문장을 지어내지 않는다")
    void 근거가_없으면_부르지_않는다() {
        DraftInput empty = new DraftInput("성진스틸", "2026-08", null, null, null, null, null,
                List.of(), List.of(), null, DraftStyle.FORMAL, null);

        assertThat(empty.hasNoBasis()).isTrue();
        assertThat(draftInputWith("electricity").hasNoBasis()).isFalse();
    }

    // ── 도우미 ────────────────────────────────────────────────

    private static ExtractionResult analyzed(ExtractionResult.Item item) {
        return new ExtractionResult(ExtractionResult.ANALYZED, null, "ko", List.of(item), List.of());
    }

    private static ExtractionResult.Item item(String key, String label, String rawValue, Object value,
                                              String unit, String failReason, String note, double confidence) {
        return new ExtractionResult.Item(key, label, rawValue, value, unit, null, failReason, note,
                confidence, new ExtractionResult.Source(null, "body:offset=10-20"));
    }

    private static DraftInput draftInputWith(String... keys) {
        List<DraftInput.MissingItem> missing = java.util.Arrays.stream(keys)
                .map(k -> new DraftInput.MissingItem(k, k, "", "기재 없음"))
                .toList();
        return new DraftInput("성진스틸", "2026-08", null, "부적격", "R2", "필수 항목 누락",
                "확인되지 않은 항목이 있습니다", missing, List.of(), null, DraftStyle.FORMAL, null);
    }

    private static DraftResult draftRequesting(String... keys) {
        List<DraftResult.RequestedItem> items = java.util.Arrays.stream(keys)
                .map(k -> new DraftResult.RequestedItem(k, k, "확인이 필요합니다"))
                .toList();
        return new DraftResult("데이터 보완 요청", List.of("담당자님께", "본문"), items, List.of("R2"), null);
    }
}
