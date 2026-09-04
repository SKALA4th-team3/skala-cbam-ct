package com.skala.cbam.task.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * 비동기 작업이 만들어 낸 자원의 종류 (API 명세 v10 №19 의 {@code resourceType}).
 *
 * <p>명세 응답은 {@code "submission"} 처럼 <b>소문자</b>다. DB 에는 대문자 이름으로 저장하고
 * JSON 으로 나갈 때만 소문자로 바꾼다 — 같은 값을 두 표기로 저장하지 않기 위해서다.
 *
 * <p>ERD 의 {@code task.resource_type} 에 이 이름 그대로 들어간다 (ADR-0012).
 */
public enum TaskResourceType {

    /** 22~25번 접수 자료 분석이 만든 제출 데이터. */
    SUBMISSION,

    /** 42·43번 초안 생성이 만든 피드백 업무 건. */
    FEEDBACK,

    /** 45번 재생성이 만든 초안 버전. */
    FEEDBACK_DRAFT,

    /** 18번 접수가 만든 메일 접수 건. */
    MAIL_RECEIPT;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
