package com.skala.cbam.task.service.port;

import java.util.List;

/**
 * №19 응답의 {@code unregisteredPartCount} 를 채우는 자리 (요구사항 25번).
 *
 * <p>이 값은 <b>제출(submission) 도메인이 소유</b>한다 — {@code unregistered_part} 테이블이 거기 있다.
 * CBAM-90(PR #22)이 아직 병합되지 않아 직접 참조할 수 없다. Supplier·Submission 때와 같은 이유,
 * 같은 해법이다.
 *
 * <p><b>CBAM-90 을 병합하는 사람에게:</b> 이 인터페이스를 구현한 {@code @Component} 를 추가하고
 * {@link NotYetImplementedUnregisteredPartCountProvider} 를 지우면 №19 가 실제 미등록 부품 수를
 * 반환한다. 그 전까지는 0 이고, 그때는 {@code resourceIds} 도 비어 있어 값이 서로 어긋나지 않는다.
 */
public interface UnregisteredPartCountProvider {

    /** 제출 건들에 남아 있는 미등록 부품(매핑되지 않은 것)의 총 개수. */
    int countUnregisteredParts(List<Long> submissionIds);
}
