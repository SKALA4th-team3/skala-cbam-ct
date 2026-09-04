package com.skala.cbam.ai.prompt;

/**
 * 요구사항 44번 문체. 시스템 프롬프트 끝에 한 줄을 덧붙인다.
 *
 * <p><b>문체는 말투만 바꾼다 — 요구하는 항목은 같아야 한다.</b> 실제 호출로 확인했다:
 * FORMAL 과 CONCISE 의 {@code requestedItems} 가 같았다.
 *
 * <p>이름은 {@code feedback.domain.FeedbackStyle} 과 같다. {@code ai} 패키지가 도메인을 모르도록
 * 따로 두고, 부르는 쪽이 {@code valueOf(style.name())} 으로 옮긴다.
 *
 * <p>⚠️ {@code FRIENDLY} 의 enum 이름은 API 명세 예시에 없다 — №26 이 {@code FORMAL},
 * №28 이 {@code CONCISE} 만 보여 준다. 팀 확인 대기이고 지어낸 이름을 명세에 쓰지 않았다.
 */
public enum DraftStyle {

    FORMAL("정중한 공문체로 씁니다. \"~해 주시기 바랍니다\" 를 씁니다. 문장을 끝까지 씁니다."),

    CONCISE("짧게 씁니다. 인사와 맺음을 한 줄로 줄이고, 항목과 기한만 분명히 적습니다."),

    FRIENDLY("평이한 존댓말로 씁니다. \"~해 주시면 됩니다\" 처럼 부담을 덜어 주는 어조를 씁니다. 과장하지 않습니다.");

    private final String instruction;

    DraftStyle(String instruction) {
        this.instruction = instruction;
    }

    public String instruction() {
        return instruction;
    }
}
