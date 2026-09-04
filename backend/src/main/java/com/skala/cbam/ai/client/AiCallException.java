package com.skala.cbam.ai.client;

/**
 * AI 호출이 실패했다. {@link #errorCode()} 는 <b>명세가 정한 값만</b> 쓴다 —
 * {@code AI_TIMEOUT} · {@code AI_ERROR} (API 명세 v10 №19). 새 코드를 만들지 않는다(ADR-0012 ④).
 *
 * <p>모델이 「읽지 못했다」고 <b>정상 응답</b>하는 것은 이 예외가 아니다 — 그건
 * {@code status: "ANALYSIS_FAILED"} 로 오고 №16 의 {@code failureReason} 으로 저장된다.
 */
public class AiCallException extends RuntimeException {

    /** 명세 №19 의 errorCode. */
    public static final String AI_TIMEOUT = "AI_TIMEOUT";
    public static final String AI_ERROR = "AI_ERROR";

    private final transient String errorCode;

    public AiCallException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiCallException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }

    /** 키가 설정되지 않았다. 코드는 AI_ERROR 다 — 명세에 없는 이름을 응답에 내보내지 않는다. */
    public static AiCallException notConfigured() {
        return new AiCallException(AI_ERROR, "AI API 키가 설정되지 않았습니다 (.env 의 AI_API_KEY)");
    }

    /** 모델이 스키마를 지키기를 거부했다. 46번 세 실패 중 둘째 — 기본 템플릿으로 간다. */
    public static AiCallException refused(String reason) {
        return new AiCallException(AI_ERROR, "모델이 응답을 거부했습니다: " + reason);
    }
}
