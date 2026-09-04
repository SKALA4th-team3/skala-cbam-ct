package com.skala.cbam.submission.service.port;

import java.util.List;
import java.util.Optional;

/**
 * 제출 데이터 API 응답 중 <b>부품(parts) 도메인이 소유한</b> 값을 채우는 자리.
 *
 * <p>부품·부품×협력업체 관계는 parts 도메인 소관인데, 이 글을 쓰는 시점엔 아직 dev 에 없다
 * (CBAM-59 브랜치에 구현돼 있지만 미병합). Supplier 도메인이 먼저 겪은 것과 같은 문제라
 * 같은 해법을 쓴다 — {@code SupplierRelatedDataProvider} / {@code NotYetImplementedDataProvider} 참고.
 *
 * <p><b>부품 도메인을 병합하는 사람에게:</b> 이 인터페이스를 구현한 @Component 를 추가하고
 * {@link NotYetImplementedPartRelatedDataProvider} 를 삭제하면 제출 데이터 API가 그대로
 * 실제 부품명·타깃 목록을 반환한다. 컨트롤러·서비스·DTO 는 고치지 않아도 된다.
 */
public interface PartRelatedDataProvider {

    /**
     * ACTIVE part_supplier 전체 중 조건에 맞는 것 (요구사항 13번 targets 와 같은 개념).
     * 제출 데이터 목록 조회(29번)가 "미제출 가상 행"을 만들 때 이 목록과 실제 제출을 대조한다.
     *
     * @param supplierId 필터, null 이면 전체
     * @param partId     필터, null 이면 전체
     */
    List<PartSupplierTarget> findActiveTargets(Long supplierId, Long partId);

    /** 실제 제출 데이터의 partSupplierId 로 부품명을 찾는다 (제출 상세 30번의 target.partName 등). */
    Optional<PartInfo> findPartInfo(Long partSupplierId);

    record PartSupplierTarget(Long partSupplierId, Long supplierId, Long partId, String partName) {
    }

    /**
     * benchmarkFactorYear = ERD part.benchmark_factor_year("현재 기준 배출원단위 적용 연도").
     * 데이터 확정(31번)이 이 값을 확정 시점 스냅샷(appliedFactorYear)으로 그대로 찍어야 한다 —
     * "확정한 날짜의 연도"가 아니다.
     */
    record PartInfo(Long partId, String partName, Integer benchmarkFactorYear) {
    }
}
