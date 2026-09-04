package com.skala.cbam.supplier.service.port;

import com.skala.cbam.supplier.dto.SupplierDetailResponse.AlertSummary;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.FeedbackHistorySummary;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.PartSummary;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.SubmissionSummary;
import com.skala.cbam.supplier.dto.SupplierSummaryResponse.MonthlyStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협력업체 상세(№4)·목록(№3)에서 <b>다른 도메인이 소유한 값</b>을 채운다.
 * <b>지금 저장소에 들어와 있는 도메인만</b> 채우고, 없는 것은 비운 채 둔다.
 *
 * <table>
 *   <tr><td>공급 부품</td><td>채운다 — 부품 도메인(part · part_supplier)이 들어왔다</td></tr>
 *   <tr><td>피드백 발송 이력</td><td>채운다 — 피드백 도메인(feedback · task)이 들어왔다</td></tr>
 *   <tr><td>제출 이력 · 월별 제출 상태 · 경보</td><td><b>비운다</b> — 해당 도메인이 아직 없다</td></tr>
 * </table>
 *
 * <p>빈 배열은 「없다」가 아니라 「아직 채울 경로가 없다」는 뜻이다. 0으로 채운 척하지 않는다 —
 * 명세 24번과 같은 태도다.
 *
 * <p><b>왜 남의 Repository 를 쓰지 않고 JPQL 인가:</b> 부품·피드백 도메인이 이미 협력업체를
 * 참조한다({@code PartsService → SupplierRepository}, {@code Feedback → Supplier}).
 * 여기서 그 패키지를 import 하면 두 방향이 맞물려 순환이 된다. 협력업체가 남의 도메인에
 * 컴파일 의존을 갖지 않는 것이 이 포트를 둔 이유이므로 엔티티 이름만으로 읽는다.
 *
 * <p>대신 <b>문자열이라 이름이 바뀌면 컴파일이 잡아 주지 못한다.</b>
 * {@code AvailableDomainDataProviderTest} 가 그 자리를 지킨다.
 *
 * <p>응답 필드 이름은 API 명세 v10 №4 의 성공 응답 예시 그대로다.
 */
@Component
@Transactional(readOnly = true)
class AvailableDomainDataProvider implements SupplierRelatedDataProvider {

    /** 공통 규약 5항 — 시각·기간은 Asia/Seoul 기준이다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 이 협력업체가 공급하는 부품.
     *
     * <p>part_supplier 에 공급 관계의 상태 컬럼이 아직 없어 <b>맺어진 관계는 모두 유효한 것으로 본다.</b>
     * 상태 컬럼이 생기면 여기에 조건을 더한다.
     */
    private static final String SUPPLIED_PARTS = """
            select p.id, p.partCode, p.partName, p.cnCode
              from Part p join p.supplierIds supplierId
             where supplierId = :supplierId
             order by p.partCode
            """;

    /** 최근 N개월 피드백 건. 발송 결과는 건마다 Task 에 따로 있어 아래 쿼리로 이어 붙인다. */
    private static final String FEEDBACKS = """
            select f.id, f.type
              from Feedback f
             where f.supplier.id = :supplierId
               and f.reportingMonth >= :from
             order by f.reportingMonth desc, f.id desc
            """;

    /**
     * 발송 시도. 피드백 도메인이 이력을 만드는 방식을 그대로 따른다
     * ({@code FeedbackService#toHistoryItem}) — 발송 결과는 Feedback 이 아니라 Task 가 갖는다.
     */
    private static final String SEND_TASKS = """
            select t.feedback.id, t.sentAt, t.deliveryStatus, t.attemptNumber
              from Task t
             where t.feedback.id in :feedbackIds
               and t.type = com.skala.cbam.feedback.domain.TaskType.SEND_FEEDBACK
            """;

    /** 한 번도 발송을 시도하지 않은 건의 상태. 공용 DeliveryStatus 의 설계 그대로다. */
    private static final String NOT_SENT_YET = "PENDING";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<PartSummary> findSuppliedParts(Long supplierId) {
        return entityManager.createQuery(SUPPLIED_PARTS, Object[].class)
                .setParameter("supplierId", supplierId)
                .getResultList()
                .stream()
                .map(row -> new PartSummary(
                        (Long) row[0], (String) row[1], (String) row[2], (String) row[3]))
                .toList();
    }

    /**
     * 최근 N개월 피드백 발송 이력 (№4).
     *
     * <p><b>기간은 reportingMonth(신고월) 기준이다.</b> 만든 날짜가 아니라 신고월로 자른다 —
     * 이 화면의 다른 칸(제출 이력 · 월별 제출 상태)이 모두 신고월로 묶여 있어 기준이 섞이면
     * 같은 화면의 「최근 12개월」이 칸마다 다른 뜻이 된다.
     * ⚠️ 명세가 둘 중 어느 것인지 못 박지 않았다. 팀 확인 대상이다.
     */
    @Override
    public List<FeedbackHistorySummary> findFeedbackHistories(Long supplierId, int months) {
        List<Object[]> feedbacks = entityManager.createQuery(FEEDBACKS, Object[].class)
                .setParameter("supplierId", supplierId)
                .setParameter("from", firstDayOfPeriod(months))
                .getResultList();
        if (feedbacks.isEmpty()) {
            return List.of();
        }

        List<Long> ids = feedbacks.stream().map(row -> (Long) row[0]).toList();
        Map<Long, List<Object[]>> tasksByFeedback = entityManager.createQuery(SEND_TASKS, Object[].class)
                .setParameter("feedbackIds", ids)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(row -> (Long) row[0]));

        return feedbacks.stream()
                .map(row -> {
                    Long feedbackId = (Long) row[0];
                    List<Object[]> tasks = tasksByFeedback.getOrDefault(feedbackId, List.of());
                    return new FeedbackHistorySummary(
                            feedbackId,
                            String.valueOf(row[1]),
                            lastSentAt(tasks),
                            latestDeliveryStatus(tasks));
                })
                .toList();
    }

    /** 마지막으로 실제 나간 시각. 한 번도 안 나갔으면 null 이다 — 0 이나 지금 시각으로 채우지 않는다. */
    private OffsetDateTime lastSentAt(List<Object[]> tasks) {
        return tasks.stream()
                .map(row -> (OffsetDateTime) row[1])
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** 가장 마지막 시도의 결과. 시도가 없으면 PENDING. */
    private String latestDeliveryStatus(List<Object[]> tasks) {
        return tasks.stream()
                .max(Comparator.comparing(row -> row[3] == null ? Short.MIN_VALUE : (Short) row[3]))
                .map(row -> row[2] == null ? NOT_SENT_YET : String.valueOf(row[2]))
                .orElse(NOT_SENT_YET);
    }

    /** 최근 N개월의 첫날. 이번 달을 포함해 N개월이다. */
    private LocalDate firstDayOfPeriod(int months) {
        return LocalDate.now(SEOUL).withDayOfMonth(1).minusMonths(months - 1L);
    }

    // ───────── 아래는 해당 도메인이 들어오면 채운다. 지금 지어내지 않는다 ─────────

    /** 제출 도메인 없음(CBAM-90 미병합). 빈 배열은 「제출이 없다」가 아니라 「채울 경로가 없다」다. */
    @Override
    public List<SubmissionSummary> findSubmissions(Long supplierId, int months) {
        return List.of();
    }

    /** 경보 도메인 없음. */
    @Override
    public List<AlertSummary> findAlerts(Long supplierId, int months) {
        return List.of();
    }

    /** 제출 도메인 없음. 화면의 12개월 스트립은 이 값이 채워져야 의미를 갖는다. */
    @Override
    public Map<Long, List<MonthlyStatus>> findMonthlyStatuses(List<Long> supplierIds, int months) {
        return Map.of();
    }

    /**
     * 빈 Set 이 아니라 {@link Optional#empty()} 여야 한다 —
     * 빈 Set 은 「조회했고 해당 업체가 없다」이고, empty 는 「조회할 경로가 없다」다.
     * 섞으면 적격 필터가 조용히 0건을 반환하는 것처럼 보인다.
     */
    @Override
    public Optional<Set<Long>> findSupplierIdsBySubmissionStatus(String submissionStatus, int months) {
        return Optional.empty();
    }

    /** 제출 도메인 없음 — 제외·보존 건수를 셀 수 없다. */
    @Override
    public SubmissionImpact countSubmissionImpact(Long supplierId) {
        return new SubmissionImpact(0, 0);
    }
}
