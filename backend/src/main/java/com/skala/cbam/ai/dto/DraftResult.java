package com.skala.cbam.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;

/**
 * 42~45번 초안. {@code docs/product/prompts/schema/draft.schema.json} 과 같은 모양이다.
 *
 * <p>모델에게는 {@link #bodyParagraphs} 문단 <b>배열</b>로 받는다. API 명세 v10 №27 의 {@code body} 는
 * <b>문자열 하나</b>라 {@link #bodyText()} 가 {@code \n\n} 으로 이어 붙인다 — 문단 경계를 모델이
 * 정하게 두면 화면이 다시 나눠야 하는데 그 규칙이 어디에도 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DraftResult(
        String subject,
        List<String> bodyParagraphs,
        List<RequestedItem> requestedItems,
        List<String> citedRuleIds,
        String dueDate
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestedItem(String key, String label, String reason) {
    }

    /** №27 의 {@code body} 로 저장할 문자열. */
    public String bodyText() {
        return bodyParagraphs == null ? "" : String.join("\n\n", bodyParagraphs);
    }

    /**
     * 요구사항 46번 세 번째 실패 — <b>스키마는 지키면서 근거 밖 항목을 요구하는 경우.</b>
     *
     * <p>구조화 출력으로 막히지 않으므로 서버가 대조해야 한다. 하나라도 나오면 이 초안을 버리고
     * 기본 템플릿으로 간다. 「없는 값을 채우자고 요구하지 않는다」가 이 대조로 지켜진다.
     *
     * <p>프런트의 {@code api/ai.js} 의 {@code unsupportedRequests()} 와 같은 검사다 —
     * {@code npm run ai:verify} 가 그쪽을 센다.
     *
     * @param allowedKeys 근거가 준 키. 추출 항목의 key 와 미등록 부품의 rawPartName
     * @return 근거에 없는 key 목록. 비어 있으면 이 초안을 써도 된다
     */
    public List<String> unsupportedRequests(Set<String> allowedKeys) {
        if (requestedItems == null) {
            return List.of();
        }
        return requestedItems.stream()
                .map(RequestedItem::key)
                .filter(key -> key != null && !key.isBlank())
                .map(String::strip)
                .filter(key -> !allowedKeys.contains(key))
                .distinct()
                .toList();
    }

    /** 제목·본문이 비면 초안이 아니다 — 확정 화면이 빈 메일을 보내게 둘 수 없다. */
    public boolean isUsable() {
        return subject != null && !subject.isBlank()
                && bodyParagraphs != null && !bodyParagraphs.isEmpty()
                && bodyParagraphs.stream().anyMatch(p -> p != null && !p.isBlank());
    }
}
