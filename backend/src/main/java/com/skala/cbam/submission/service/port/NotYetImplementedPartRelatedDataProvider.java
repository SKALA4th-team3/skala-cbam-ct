package com.skala.cbam.submission.service.port;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 부품(parts) 도메인이 병합되기 전까지 쓰는 임시 구현. 목록은 항상 비어 있다.
 *
 * <p>이 상태에서 제출 목록 조회(29번)의 "미제출 가상 행"은 하나도 안 생긴다 — 데이터가 없는 게
 * 아니라 아직 채울 경로가 없다는 뜻이다(명세 24번이 요구하는 태도와 같다).
 *
 * <p>제거 조건: {@link PartRelatedDataProvider} 를 구현한 실제 어댑터가 등록되면 이 클래스를 삭제한다.
 */
@Component
class NotYetImplementedPartRelatedDataProvider implements PartRelatedDataProvider {

    @Override
    public List<PartSupplierTarget> findActiveTargets(Long supplierId, Long partId) {
        return List.of();
    }

    @Override
    public Optional<PartInfo> findPartInfo(Long partSupplierId) {
        return Optional.empty();
    }
}
