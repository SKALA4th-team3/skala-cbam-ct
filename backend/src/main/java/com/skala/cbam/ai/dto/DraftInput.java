package com.skala.cbam.ai.dto;

import com.skala.cbam.ai.prompt.DraftStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 42~45번 초안에 넣는 것. {@code docs/product/prompts/02-feedback-draft.md} 의 「사용자 메시지」 그대로다.
 *
 * <p><b>빈 절을 「없음」으로 채워 넣지 않는다.</b> 절이 없으면 근거가 없다는 뜻이고, 모델이
 * 「없음」이라는 글자를 근거로 오해하지 않는다.
 *
 * @param missingItems 확인되지 않은 항목. 이것과 {@link #unregisteredParts} 가 요청해도 되는 것의 전부다
 * @param instruction 45번 담당자 추가 지시. 근거 밖을 요구하라고 해도 따르지 않는다 —
 *                    실제로 「용수·폐기물·작년 실적도 요청해줘」를 넣어 보고 확인했다
 */
public record DraftInput(
        String supplierName,
        String period,
        String dueDate,
        String judgement,
        String ruleId,
        String ruleName,
        String why,
        List<MissingItem> missingItems,
        List<String> unregisteredParts,
        String rejectReason,
        DraftStyle style,
        String instruction
) {

    /**
     * @param rawValue 원문 표기. 비어 있으면 「원문에 값이 없다」(R2),
     *                 값이 있으면 「값은 있는데 못 옮겼다」(R5) — 안내문이 다르게 써야 한다
     */
    public record MissingItem(String key, String label, String rawValue, String note) {
    }

    /**
     * 요구해도 되는 항목 키. {@link DraftResult#unsupportedRequests(Set)} 가 이것과 대조한다.
     * 프런트의 {@code api/ai.js} 의 {@code allowedRequestKeys()} 와 같은 집합이다.
     */
    public Set<String> allowedRequestKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (missingItems != null) {
            missingItems.stream()
                    .filter(item -> item != null && item.key() != null && !item.key().isBlank())
                    .forEach(item -> keys.add(item.key().strip()));
        }
        if (unregisteredParts != null) {
            unregisteredParts.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(name -> keys.add(name.strip()));
        }
        return keys;
    }

    /** 요구할 것이 하나도 없으면 초안을 AI 로 만들 이유가 없다 — 기본 템플릿이 맞다. */
    public boolean hasNoBasis() {
        return allowedRequestKeys().isEmpty()
                && (rejectReason == null || rejectReason.isBlank())
                && (why == null || why.isBlank());
    }
}
