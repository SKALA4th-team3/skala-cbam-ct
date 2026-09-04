package com.skala.cbam.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.skala.cbam.ai.client.AiCallException;
import com.skala.cbam.ai.client.AiClient;
import com.skala.cbam.ai.dto.DraftInput;
import com.skala.cbam.ai.dto.DraftResult;
import com.skala.cbam.ai.dto.ExtractionInput;
import com.skala.cbam.ai.dto.ExtractionResult;
import com.skala.cbam.ai.prompt.UserMessages;
import com.skala.cbam.ai.schema.AiAssets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 서비스 안에서 <b>모델을 부르는 자리는 여기 둘뿐이다</b> (docs/product/AI_EXTENSION.md).
 *
 * <ol>
 *   <li>{@link #extract} — 22~25번 접수 자료에서 배출 항목을 뽑는다 (UC-05)</li>
 *   <li>{@link #draft} — 42~45번 협력사에 보낼 안내문 초안을 쓴다 (UC-10)</li>
 * </ol>
 *
 * <p><b>판정하지 않는다.</b> 33~37번은 규칙이지 AI 가 아니다 — 응답 스키마에 {@code judgement}·
 * {@code severity}·{@code rule} 이 없다 (ADR-0010 ①).
 *
 * <p>{@code temperature} 가 다른 이유(ADR-0010 ②) — 추출은 규제 신고 데이터라 <b>같은 입력에 같은
 * 출력</b>이어야 하는 계산이고(0), 안내문은 사람이 읽을 문장이라 매번 같을 이유가 없다(0.5).
 * 대신 무엇을 요구하는지는 {@link DraftResult#unsupportedRequests} 가 잡는다.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    /** 규제 신고 데이터다. 같은 입력에 같은 출력이어야 한다. */
    private static final double EXTRACTION_TEMPERATURE = 0.0;
    /** 문장을 쓰는 일이라 0 이면 딱딱하다. 근거는 스키마와 서버 대조가 잡는다. */
    private static final double DRAFT_TEMPERATURE = 0.5;

    private final AiClient aiClient;
    private final AiAssets assets;
    private final ObjectMapper objectMapper;

    public AiService(AiClient aiClient, AiAssets assets, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.assets = assets;
        this.objectMapper = objectMapper;
    }

    /** 키가 설정돼 있는가. false 면 부르는 쪽이 46번 기본 템플릿으로 바로 간다. */
    public boolean isAvailable() {
        return aiClient.isAvailable();
    }

    /**
     * 22~25번 — 접수 자료에서 배출 항목을 뽑아 표준 단위로 옮기고 등록 부품과 맞춰 본다.
     *
     * <p>읽을 것이 아예 없으면 <b>모델을 부르지 않는다</b> — 빈 본문을 보내 「없다」는 답을 돈 주고
     * 받을 이유가 없다. №16 의 {@code NO_ATTACHMENT} 로 바로 끝낸다.
     *
     * @throws AiCallException 호출 실패·시간 초과. 부르는 쪽이 Task 를 FAILED 로 남긴다
     */
    public ExtractionResult.Normalized extract(ExtractionInput input) {
        if (input.hasNothingToRead()) {
            log.info("읽을 본문도 첨부 텍스트도 없어 AI 를 부르지 않는다");
            return new ExtractionResult.Normalized(
                    new ExtractionResult(ExtractionResult.ANALYSIS_FAILED, "NO_ATTACHMENT",
                            "unknown", List.of(), List.of()),
                    List.of("본문·첨부 텍스트가 비어 모델을 부르지 않았다"));
        }

        JsonNode json = aiClient.complete(
                assets.extractionSystemPrompt(),
                UserMessages.forExtraction(input),
                AiAssets.EXTRACTION_SCHEMA_NAME,
                assets.extractionSchema(),
                EXTRACTION_TEMPERATURE);

        ExtractionResult raw = convert(json, ExtractionResult.class);
        ExtractionResult.Normalized normalized = raw.normalize();

        // 서버가 무엇을 고쳤는지 남긴다 — 모델 바꿨을 때 조용히 나빠지는 것을 여기서 본다
        if (!normalized.repairs().isEmpty()) {
            log.warn("추출 응답을 서버가 고쳤다: {}", normalized.repairs());
        }
        return normalized;
    }

    /**
     * 42~45번 — 판정 사유와 반려 사유를 근거로 안내문 초안을 쓴다.
     *
     * <p>돌려준 초안이 <b>근거 밖 항목을 요구하면 버린다</b>(46번 세 번째 실패). 스키마로 막히지
     * 않는 실패라 서버가 대조한다 — 부르는 쪽은 {@code null} 을 받으면 기본 템플릿으로 간다.
     *
     * @return 쓸 수 있는 초안. 근거 밖을 요구했거나 제목·본문이 비면 {@code null}
     * @throws AiCallException 호출 실패·시간 초과
     */
    public DraftResult draft(DraftInput input) {
        String systemPrompt = assets.draftSystemPrompt()
                + "\n\n" + input.style().instruction()
                + UserMessages.instructionBlock(input.instruction());

        JsonNode json = aiClient.complete(
                systemPrompt,
                UserMessages.forDraft(input),
                AiAssets.DRAFT_SCHEMA_NAME,
                assets.draftSchema(),
                DRAFT_TEMPERATURE);

        DraftResult result = convert(json, DraftResult.class);

        if (!result.isUsable()) {
            log.warn("초안의 제목 또는 본문이 비어 있어 버린다");
            return null;
        }

        // 실제 호출에서 겪은 것 — 모델이 「기간」(2026-08)을 회신 기한으로 옮겨 적어
        // 「이미 지난 날짜까지 회신하라」는 문장을 만들었다. 본문의 날짜는 서버가 고칠 수 없으므로
        // 초안을 통째로 버린다. 기본 템플릿에는 기한이 없다
        if (!java.util.Objects.equals(input.dueDate(), result.dueDate())) {
            log.warn("초안의 회신 기한이 준 값과 다르다 (준 값 {}, 응답 {}) — 버린다",
                    input.dueDate(), result.dueDate());
            return null;
        }

        List<String> unsupported = result.unsupportedRequests(input.allowedRequestKeys());
        if (!unsupported.isEmpty()) {
            // 없는 값을 채우자고 요구하면 협력사가 보내지 않아도 될 자료를 보내게 된다
            log.warn("초안이 근거 밖 항목을 요구해 버린다: {} (허용: {})",
                    unsupported, input.allowedRequestKeys());
            return null;
        }
        return result;
    }

    private <T> T convert(JsonNode json, Class<T> type) {
        try {
            return objectMapper.treeToValue(json, type);
        } catch (Exception e) {
            throw new AiCallException(AiCallException.AI_ERROR,
                    "AI 응답을 " + type.getSimpleName() + " 로 읽지 못했습니다", e);
        }
    }
}
