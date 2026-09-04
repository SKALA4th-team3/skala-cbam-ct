package com.skala.cbam.mail.service.port;

import java.util.List;

/**
 * 접수 이력 API 응답 중 <b>제출(submission) 도메인이 소유한</b> 값을 채우는 자리.
 *
 * <p>접수 목록(80번)의 submissionIds 는 이 접수 메일에서 생성된 제출 데이터 id 들이다
 * (CBAM-90 소관). CBAM-90 이 아직 이 브랜치(dev)에 병합되지 않아 직접 참조할 수 없다 —
 * Supplier/PartRelatedDataProvider 와 같은 이유, 같은 해법.
 *
 * <p><b>CBAM-90 이 병합되는 사람에게:</b> 이 인터페이스를 구현한 @Component 를 추가하고
 * {@link NotYetImplementedMailRelatedDataProvider} 를 삭제하면 접수 목록이 그대로 실제
 * submissionIds 를 반환한다.
 */
public interface MailRelatedDataProvider {

    /** 이 접수 메일에서 생성된 제출 데이터 id 목록 (80번 submissionIds). */
    List<Long> findSubmissionIds(Long mailReceiptId);
}
