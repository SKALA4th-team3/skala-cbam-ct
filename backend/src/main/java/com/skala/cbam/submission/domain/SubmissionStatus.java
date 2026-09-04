package com.skala.cbam.submission.domain;

/**
 * 제출 데이터 처리 상태. 코드·Enum 정의 시트: "status = 자료의 처리 단계 / judgement = 적격 판정
 * 결과. 두 값 집합은 겹치지 않는다."
 *
 * <p>NOT_SUBMITTED 는 진짜 저장된 행일 수도(반려로 무효 환원된 경우), 목록 조회에서 조회 시점에
 * 계산해서 끼워 넣는 가상 행(id=null)일 수도 있다 — API 명세 20행 참고.
 */
public enum SubmissionStatus {
    NOT_SUBMITTED,
    REVIEW_PENDING,
    CONFIRMED,
    REJECTED
}
