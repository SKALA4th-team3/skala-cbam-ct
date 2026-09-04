package com.skala.cbam.task.error;

import org.springframework.http.HttpStatus;

/** №19 작업 조회가 던지는 에러 code. 도메인 전용 enum(FeedbackErrorCode 와 같은 이유). */
public enum TaskErrorCode {

    /** 404 — 그런 작업이 없다. <b>작업이 실패한 것과 다르다</b> — 실패는 200 + status=FAILED 다. */
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 작업을 찾을 수 없습니다");

    private final HttpStatus status;
    private final String defaultMessage;

    TaskErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
