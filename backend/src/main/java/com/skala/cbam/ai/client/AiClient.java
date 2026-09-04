package com.skala.cbam.ai.client;

import tools.jackson.databind.JsonNode;

/**
 * 구조화 출력으로 모델을 한 번 부른다.
 *
 * <p>도메인을 모른다 — 프롬프트와 스키마를 받아 JSON 을 돌려줄 뿐이다. 제공자를 바꾸면
 * 이 인터페이스의 구현만 갈아 끼운다(ADR-0013 ①).
 */
public interface AiClient {

    /**
     * @param systemPrompt 시스템 프롬프트
     * @param userMessage 사용자 메시지
     * @param schemaName 구조화 출력의 {@code json_schema.name} — 예 {@code cbam_extraction}
     * @param schema {@code strict: true} 를 만족하는 JSON Schema
     * @param temperature 추출은 0(같은 입력에 같은 출력), 문안 작성은 0.5 (ADR-0010 ②)
     * @return 스키마를 지킨 JSON
     * @throws AiCallException 호출 실패·시간 초과·거부. 호출한 쪽이 46번 대체 경로를 결정한다
     */
    JsonNode complete(String systemPrompt, String userMessage, String schemaName,
                      JsonNode schema, double temperature);

    /** 키가 설정돼 있어 실제로 부를 수 있는가. false 면 부르지 않고 바로 기본 템플릿으로 간다. */
    boolean isAvailable();
}
