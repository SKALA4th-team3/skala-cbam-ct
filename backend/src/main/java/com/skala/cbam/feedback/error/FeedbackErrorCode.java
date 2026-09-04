package com.skala.cbam.feedback.error;

import org.springframework.http.HttpStatus;

/** 피드백 API(№26~№31)가 던지는 에러 code. 도메인 전용 enum(SupplierErrorCode와 같은 이유). */
public enum FeedbackErrorCode {

    /** 400 — 대상 없음 (№26 초안 생성). */
    NO_TARGET(HttpStatus.BAD_REQUEST, "초안을 생성할 대상이 없습니다"),

    /** 400 — 부적격·미제출 건이 아닌 대상 포함 (№26). */
    NOT_DRAFTABLE(HttpStatus.BAD_REQUEST, "부적격·미제출 건이 아니어서 초안을 생성할 수 없습니다"),

    /** 404 — 제출 데이터 없음 (№26). */
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출 데이터를 찾을 수 없습니다"),

    /** 404 — 초안(피드백 업무 건) 없음 (№27~№30). */
    FEEDBACK_DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "초안을 찾을 수 없습니다"),

    /** 404 — 지정한 버전 없음 (№27). */
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "지정한 버전을 찾을 수 없습니다"),

    /** 400 — 확정·발송 후 재생성 불가 (№28). */
    NOT_REGENERATABLE(HttpStatus.BAD_REQUEST, "재생성할 수 없는 상태입니다"),

    /** 400 — 확정할 수 없는 상태(DRAFT 가 아님, 제목·본문 없음 등) (№29). */
    NOT_CONFIRMABLE(HttpStatus.BAD_REQUEST, "확정할 수 없는 상태입니다"),

    /** 409 — 이미 확정됨 (№29·№30). */
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 피드백입니다"),

    /** 400 — 확정되지 않은 초안 (№30 발송). */
    NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "확정되지 않은 초안입니다"),

    /** 400 — 재발송인데 reason 누락 (№30). */
    RESEND_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "재발송 시 reason 이 필요합니다"),

    /** 400 — SEND_FAILED·NO_REPLY 외 값 (№30). */
    INVALID_RESEND_REASON(HttpStatus.BAD_REQUEST, "reason 은 SEND_FAILED 또는 NO_REPLY 여야 합니다"),

    /** 400 — NO_REPLY 재발송 대기기간 미경과 (№30). */
    RESEND_TOO_EARLY(HttpStatus.BAD_REQUEST, "아직 재발송할 수 없습니다"),

    /** 502 — 메일 발송 시스템 오류 (№30). */
    MAIL_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "메일 발송 서비스에 연결할 수 없습니다"),

    /** 404 — 협력업체 없음 (№31). */
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "협력업체를 찾을 수 없습니다"),

    /** 400 — 필터·페이지 파라미터 형식 오류. */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다");

    private final HttpStatus status;
    private final String defaultMessage;

    FeedbackErrorCode(HttpStatus status, String defaultMessage) {
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
