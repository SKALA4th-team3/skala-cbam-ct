package com.skala.cbam.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 호출 설정 (ADR-0012). 값은 {@code .env} 의 {@code AI_BASE_URL}·{@code AI_API_KEY}·
 * {@code AI_MODEL} 에서 온다 — {@code application.yml} 이 그 이름들을 참조한다.
 *
 * <p><b>키가 없어도 앱은 뜬다</b>(ADR-0012 ③). {@link #isConfigured()} 가 false 면 AI 호출이
 * 즉시 실패하고 초안은 요구사항 46번의 기본 템플릿으로 간다. dev 프로필과 테스트가 키 없이
 * 돌아야 하기 때문이다.
 *
 * @param baseUrl OpenAI 호환 엔드포인트. 제공자를 바꿔도 여기와 {@code OpenAiClient} 만 본다
 * @param apiKey 절대 로그에 찍지 않는다. 프런트에도 보내지 않는다 — {@code VITE_} 값은 번들에 박힌다
 * @param model 구조화 출력({@code json_schema} · {@code strict})을 지원하는 모델이어야 한다
 * @param timeout 읽기 타임아웃. 스트리밍이라 토큰이 계속 오는 동안은 갱신된다(ADR-0012 ②)
 */
@ConfigurationProperties(prefix = "cbam.ai")
public record AiProperties(
        String baseUrl,
        String apiKey,
        String model,
        Duration timeout
) {

    public AiProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com/v1" : baseUrl.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = (model == null || model.isBlank()) ? "gpt-4o-mini" : model.trim();
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    /** 키가 채워져 있는가. 없으면 AI 를 부르지 않고 바로 기본 템플릿으로 간다. */
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String chatCompletionsUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/chat/completions";
    }
}
