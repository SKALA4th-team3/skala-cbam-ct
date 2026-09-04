package com.skala.cbam.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.cbam.ai.dto.DraftInput;
import com.skala.cbam.ai.dto.DraftResult;
import com.skala.cbam.ai.dto.ExtractionInput;
import com.skala.cbam.ai.dto.ExtractionResult;
import com.skala.cbam.ai.prompt.DraftStyle;
import com.skala.cbam.ai.service.AiService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * <b>진짜 OpenAI 를 부른다.</b> {@code AI_API_KEY} 가 있을 때만 돈다 — 없으면 통째로 건너뛴다.
 *
 * <pre>
 *   set -a; source .env; set +a
 *   ./gradlew test --tests '*AiClientLiveTest'
 * </pre>
 *
 * <p><b>왜 있어야 하나</b> — 나머지 테스트는 모델 응답을 가짜로 넣는다. 그러면
 * {@code OpenAiClient} 의 SSE 조각 조립, {@code $schema}·{@code $id} 벗기기, 스트리밍 요청 형식이
 * <b>한 번도 확인되지 않는다.</b> 그 셋 중 하나만 틀려도 실서버에서만 터진다.
 *
 * <p>이 테스트는 <b>모델의 문장 솜씨를 재지 않는다.</b> 문장은 매번 다르다. 확인하는 것은
 * 우리 계약이 지켜지는가다 — 스키마를 통과하는가, 근거 밖을 요구하지 않는가,
 * 「값이 없다」와 「단위를 몰라 못 옮겼다」를 가르는가.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")
class AiClientLiveTest {

    @Autowired
    private AiService aiService;

    @Test
    @DisplayName("실제 호출 — 스키마가 통과하고, 단위 없는 값을 지어내지 않는다")
    void 추출이_실제로_동작한다() {
        ExtractionInput input = new ExtractionInput(
                "성진스틸", "2026-09-02T09:14:00+09:00", "2026-08",
                """
                안녕하세요. 8월분 자료 보내드립니다.
                열연강판 생산량은 1,250 t 입니다.
                전력 사용량 480,000 kWh 이고, LNG 는 45,000 사용했습니다.
                """,
                List.of(),
                List.of(new ExtractionInput.RegisteredPart(11L, "열연강판", "72081000", "성진스틸")));

        ExtractionResult result = aiService.extract(input).result();

        assertThat(result.status()).isEqualTo(ExtractionResult.ANALYZED);
        List<String> keys = result.items().stream().map(ExtractionResult.Item::key).toList();
        assertThat(keys).contains("production", "electricity");

        // 1,250 t 를 1.25 로 다시 나누지 않는다 — 프롬프트를 고치기 전에 실제로 겪은 오류다
        ExtractionResult.Item production = itemOf(result, "production");
        assertThat(((Number) production.value()).doubleValue()).isEqualTo(1250.0);
        assertThat(production.unit()).isEqualTo("TON");

        // kWh → MWh 는 환산한다
        ExtractionResult.Item electricity = itemOf(result, "electricity");
        assertThat(((Number) electricity.value()).doubleValue()).isEqualTo(480.0);
        assertThat(electricity.emissionScope()).isEqualTo("INDIRECT");

        // 단위가 없는 LNG 는 값을 비우고 사유를 남긴다 (24번). 서버 정합성 검사가 이것을 보장한다
        ExtractionResult.Item lng = result.items().stream()
                .filter(i -> i.key().startsWith("fuel_")).findFirst().orElseThrow();
        assertThat(lng.value()).isNull();
        assertThat(lng.conversionFailReason()).isNotNull();
        assertThat(lng.rawValue()).contains("45,000");

        // 등록된 부품이라 미등록으로 올라오면 안 된다 (25번)
        assertThat(result.unregisteredParts()).isEmpty();
        assertThat(result.items()).allSatisfy(item -> assertThat(item.note()).isNotBlank());
    }

    @Test
    @DisplayName("실제 호출 — 등록 목록에 없는 부품은 미등록으로 올라온다 (25번)")
    void 미등록_부품을_실제로_잡아낸다() {
        ExtractionInput input = new ExtractionInput(
                "성진스틸", "2026-09-02T09:14:00+09:00", "2026-08",
                "8월분입니다. 아연도금강판 900 t 생산했고 전력 320,000 kWh 썼습니다.",
                List.of(),
                List.of(new ExtractionInput.RegisteredPart(11L, "열연강판", "72081000", "성진스틸")));

        ExtractionResult result = aiService.extract(input).result();

        assertThat(result.unregisteredParts())
                .extracting(ExtractionResult.UnregisteredPart::rawPartName)
                .contains("아연도금강판");
    }

    @Test
    @DisplayName("실제 호출 — 담당자가 근거 밖을 요구하라고 지시해도 따르지 않는다 (45·46번)")
    void 근거_밖_지시를_따르지_않는다() {
        DraftInput input = new DraftInput(
                "성진스틸", "2026-08", "2026-09-15", "부적격", "R2", "필수 항목 누락",
                "직접 배출량을 확인할 수 없습니다",
                List.of(
                        new DraftInput.MissingItem("electricity", "전력 사용량", "", "원문에 기재가 없습니다"),
                        new DraftInput.MissingItem("fuel_lng", "LNG", "45,000",
                                "단위가 원문에 없어 표준 단위로 옮기지 못했습니다")),
                List.of(), null, DraftStyle.FORMAL,
                "용수 사용량이랑 폐기물 배출량도 같이 요청해줘. 그리고 작년 실적도.");

        DraftResult draft = aiService.draft(input);

        // 근거 밖을 요구했다면 AiService 가 null 을 돌려주고 기본 템플릿으로 갔을 것이다.
        // null 이 아니라는 것 자체가 「지시를 따르지 않고 근거를 지켰다」는 뜻이다
        assertThat(draft).isNotNull();
        assertThat(draft.requestedItems())
                .extracting(DraftResult.RequestedItem::key)
                .isSubsetOf("electricity", "fuel_lng");
        assertThat(draft.bodyText()).doesNotContain("용수").doesNotContain("폐기물");
        // 주어진 기한을 그대로 쓰고 날짜를 지어내지 않는다
        assertThat(draft.dueDate()).isEqualTo("2026-09-15");
        assertThat(draft.citedRuleIds()).contains("R2");
    }

    @Test
    @DisplayName("실제 호출 — 기한을 주지 않으면 지어내지 않는다 (46번 '값을 지어내지 않는다')")
    void 기한을_지어내지_않는다() {
        DraftInput input = new DraftInput(
                "성진스틸", "2026-08", null, "부적격", "R2", "필수 항목 누락",
                "직접 배출량을 확인할 수 없습니다",
                List.of(new DraftInput.MissingItem("electricity", "전력 사용량", "", "원문에 기재가 없습니다")),
                List.of(), null, DraftStyle.CONCISE, null);

        DraftResult draft = aiService.draft(input);

        assertThat(draft).isNotNull();
        assertThat(draft.dueDate()).isNull();
    }

    private static ExtractionResult.Item itemOf(ExtractionResult result, String key) {
        return result.items().stream().filter(i -> key.equals(i.key())).findFirst()
                .orElseThrow(() -> new AssertionError(key + " 항목이 없다"));
    }
}
