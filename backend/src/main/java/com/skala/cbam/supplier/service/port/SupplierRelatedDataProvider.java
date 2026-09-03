package com.skala.cbam.supplier.service.port;

import com.skala.cbam.supplier.dto.SupplierDetailResponse.AlertSummary;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.FeedbackHistorySummary;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.PartSummary;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.SubmissionSummary;
import com.skala.cbam.supplier.dto.SupplierSummaryResponse.MonthlyStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 협력업체 API 응답 중 <b>다른 도메인이 소유한</b> 값을 채우는 자리.
 *
 * <p>공급 부품은 part · part_supplier, 제출 이력과 월별 제출 상태는 submission,
 * 경보는 alert, 피드백 발송 이력은 feedback 테이블에서 나온다. 그 도메인들은 아직 구현되지 않았고
 * 소유자도 다르다. 협력업체 담당이 그 테이블까지 선점해 만들면 같은 파일에서 충돌한다.
 * 그래서 <b>응답 계약은 명세대로 유지하고 데이터 공급만</b> 이 인터페이스로 미룬다.
 *
 * <p><b>해당 도메인을 만드는 사람에게:</b> 이 인터페이스를 구현한 @Component 를 추가하고
 * {@link NotYetImplementedDataProvider} 를 삭제하면 협력업체 API가 그대로 실 데이터를 반환한다.
 * 컨트롤러·서비스·DTO 는 고치지 않아도 된다.
 *
 * <p>months 는 조회 기간(최근 N개월)이다. 기준월 계산은 구현체가 한다 —
 * 어느 시점을 "현재 월"로 볼지는 마감(요구사항 16번) 규칙을 아는 쪽이 정해야 한다.
 */
public interface SupplierRelatedDataProvider {

    /** 협력업체가 현재 공급 중인 부품 목록 (№4). ACTIVE 인 공급 관계만 담아야 한다. */
    List<PartSummary> findSuppliedParts(Long supplierId);

    /** 최근 N개월 제출 이력 (№4). */
    List<SubmissionSummary> findSubmissions(Long supplierId, int months);

    /** 최근 N개월 수신 경보 (№4). */
    List<AlertSummary> findAlerts(Long supplierId, int months);

    /** 최근 N개월 피드백 발송 이력 (№4). */
    List<FeedbackHistorySummary> findFeedbackHistories(Long supplierId, int months);

    /** 업체별 월별 제출 상태 (№3). 목록 한 페이지분을 한 번에 묻는다 — 행마다 조회하면 N+1이 된다. */
    Map<Long, List<MonthlyStatus>> findMonthlyStatuses(List<Long> supplierIds, int months);

    /**
     * 적격 상태 필터에 해당하는 협력업체 id 집합 (№3의 submissionStatus).
     *
     * <p>{@link Optional#empty()} 는 "판정 데이터를 조회할 경로가 아직 없다"는 뜻이다.
     * 빈 Set 과 구분해야 한다 — 빈 Set 은 "조회했고 해당 업체가 없다"이다.
     */
    Optional<Set<Long>> findSupplierIdsBySubmissionStatus(String submissionStatus, int months);

    /** 협력 끊김 전환 시 제외·보존되는 제출 건수 (№2). */
    SubmissionImpact countSubmissionImpact(Long supplierId);

    /**
     * @param excludedCount  마감 대상·미제출 경보에서 제외되는 건수
     * @param preservedCount 삭제하지 않고 보존하는 기존 제출 건수
     */
    record SubmissionImpact(int excludedCount, int preservedCount) {
    }
}
