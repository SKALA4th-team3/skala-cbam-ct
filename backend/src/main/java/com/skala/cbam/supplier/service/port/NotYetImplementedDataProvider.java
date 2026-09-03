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
import org.springframework.stereotype.Component;

/**
 * 부품 · 제출 · 경보 · 피드백 도메인이 붙기 전까지 쓰는 임시 구현.
 * <b>목록은 항상 비어 있고, 건수는 항상 0이다.</b>
 *
 * <p>응답에서 키는 사라지지 않고 빈 배열로 나가므로 화면 계약은 지금도 성립한다.
 * 다만 <b>값은 비어 있다</b> — 데이터가 없는 것이 아니라 아직 채울 경로가 없다는 뜻이다.
 * 명세 24번이 요구하는 태도와 같다: 모르면 채우지 말고 비운다.
 *
 * <p>적격 상태 필터만 {@link Optional#empty()} 로 "조회 경로 없음"을 명시적으로 알린다.
 * 여기서 빈 Set 을 주면 서비스가 "그런 업체가 없다"로 오해해 조용히 0건을 반환하게 된다.
 *
 * <p>제거 조건: {@link SupplierRelatedDataProvider} 를 구현한 실제 어댑터가 등록되면
 * 이 클래스를 삭제한다. 두 개가 동시에 있으면 빈 값이 이겨 조용히 잘못된 응답이 나가므로
 * 남겨 두지 않는다.
 */
@Component
class NotYetImplementedDataProvider implements SupplierRelatedDataProvider {

    @Override
    public List<PartSummary> findSuppliedParts(Long supplierId) {
        return List.of();
    }

    @Override
    public List<SubmissionSummary> findSubmissions(Long supplierId, int months) {
        return List.of();
    }

    @Override
    public List<AlertSummary> findAlerts(Long supplierId, int months) {
        return List.of();
    }

    @Override
    public List<FeedbackHistorySummary> findFeedbackHistories(Long supplierId, int months) {
        return List.of();
    }

    @Override
    public Map<Long, List<MonthlyStatus>> findMonthlyStatuses(List<Long> supplierIds, int months) {
        return Map.of();
    }

    @Override
    public Optional<Set<Long>> findSupplierIdsBySubmissionStatus(String submissionStatus, int months) {
        return Optional.empty();
    }

    @Override
    public SubmissionImpact countSubmissionImpact(Long supplierId) {
        return new SubmissionImpact(0, 0);
    }
}
