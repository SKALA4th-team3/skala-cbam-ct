package com.skala.cbam.supplier.service;

import com.skala.cbam.supplier.error.SupplierErrorCode;
import com.skala.cbam.supplier.error.SupplierException;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.domain.SupplierStatus;
import com.skala.cbam.supplier.dto.PageResponse;
import com.skala.cbam.supplier.dto.SupplierCreateRequest;
import com.skala.cbam.supplier.dto.SupplierCreateResponse;
import com.skala.cbam.supplier.dto.SupplierDetailResponse;
import com.skala.cbam.supplier.dto.SupplierSearchCondition;
import com.skala.cbam.supplier.dto.SupplierSummaryResponse;
import com.skala.cbam.supplier.dto.SupplierSummaryResponse.MonthlyStatus;
import com.skala.cbam.supplier.dto.SupplierUpdateRequest;
import com.skala.cbam.supplier.dto.SupplierUpdateResponse;
import com.skala.cbam.supplier.repository.SupplierRepository;
import com.skala.cbam.supplier.repository.SupplierSpecifications;
import com.skala.cbam.supplier.service.port.SupplierRelatedDataProvider;
import com.skala.cbam.supplier.service.port.SupplierRelatedDataProvider.SubmissionImpact;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협력업체 등록 · 수정 · 조회 서비스 (API 명세 №1~№4 · 요구사항 1~6번).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierRelatedDataProvider dataProvider;

    /**
     * 협력업체 등록 (№1 · 요구사항 1번).
     *
     * <p>사업자등록번호와 담당자 이메일은 중복 등록할 수 없다.
     * 이메일은 수신 메일 매칭 키이므로 소문자로 정규화해 비교·저장한다.
     *
     * @throws SupplierException 409 DUPLICATE_BUSINESS_NUMBER · DUPLICATE_CONTACT_EMAIL
     */
    @Transactional
    public SupplierCreateResponse createSupplier(SupplierCreateRequest request) {
        if (supplierRepository.existsByBusinessRegistrationNumber(request.businessRegistrationNumber())) {
            throw new SupplierException(SupplierErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }
        String email = Supplier.normalizeEmail(request.contactEmail());
        if (supplierRepository.existsByContactEmail(email)) {
            throw new SupplierException(SupplierErrorCode.DUPLICATE_CONTACT_EMAIL);
        }

        Supplier supplier = supplierRepository.save(Supplier.builder()
                .businessRegistrationNumber(request.businessRegistrationNumber())
                .name(request.companyName())
                .countryCode(request.country())
                .contactName(request.contactName())
                .contactEmail(email)
                .contactPhone(request.phone())
                .build());

        return SupplierCreateResponse.from(supplier);
    }

    /**
     * 협력업체 수정 · 협력 끊김 처리 (№2 · 요구사항 2번 · 6번).
     *
     * <p>status 를 보내면 상태 전이가, 보내지 않으면 정보 수정만 일어난다.
     * 이전 이메일로 접수된 이력과 기존 제출 데이터는 그대로 보존한다 — 여기서 아무것도 지우지 않는다.
     *
     * @throws SupplierException 404 SUPPLIER_NOT_FOUND · 400 INVALID_STATUS · 400 INVALID_REQUEST
     *                           · 409 DUPLICATE_CONTACT_EMAIL
     */
    @Transactional
    public SupplierUpdateResponse updateSupplier(Long supplierId, SupplierUpdateRequest request) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

        if (request.contactEmail() != null) {
            String email = Supplier.normalizeEmail(request.contactEmail());
            if (supplierRepository.existsByContactEmailAndIdNot(email, supplierId)) {
                throw new SupplierException(SupplierErrorCode.DUPLICATE_CONTACT_EMAIL);
            }
        }
        supplier.updateContact(request.contactName(), request.contactEmail(), request.phone());

        if (request.status() != null) {
            applyStatusTransition(supplier, request);
        }

        SubmissionImpact impact = dataProvider.countSubmissionImpact(supplierId);
        return SupplierUpdateResponse.of(supplier, impact.excludedCount(), impact.preservedCount());
    }

    /**
     * 협력업체 리스트 조회 (№3 · 요구사항 3번).
     *
     * <p>적격 상태 필터(submissionStatus)는 제출 도메인이 판정한다. 그 조회 경로가 아직 없으면
     * <b>필터를 무시하지 않고 빈 결과를 반환한다</b> — 필터를 못 거는데 전체를 돌려주면
     * 화면은 걸러진 결과로 오해한다. 명세 24번과 같은 태도다: 모르면 채우지 않는다.
     */
    public PageResponse<SupplierSummaryResponse> searchSuppliers(
            SupplierSearchCondition condition, Pageable pageable) {

        Page<Supplier> page = findPage(condition, pageable);
        Map<Long, List<MonthlyStatus>> monthlyStatuses = page.isEmpty()
                ? Map.of()
                : dataProvider.findMonthlyStatuses(page.map(Supplier::getId).getContent(), condition.months());

        List<SupplierSummaryResponse> content = page.getContent().stream()
                .map(supplier -> new SupplierSummaryResponse(
                        supplier.getId(),
                        supplier.getName(),
                        supplier.getCountryCode(),
                        supplier.getStatus(),
                        monthlyStatuses.getOrDefault(supplier.getId(), List.of())))
                .toList();

        return PageResponse.of(page, content);
    }

    /**
     * 협력업체 상세 조회 (№4 · 요구사항 5번).
     *
     * <p>공급 부품 · 제출 이력 · 경보 · 피드백 발송 이력은
     * {@link SupplierRelatedDataProvider} 가 채운다. 해당 도메인이 아직 없어 지금은 모두 빈 배열이다.
     *
     * @throws SupplierException 404 SUPPLIER_NOT_FOUND
     */
    public SupplierDetailResponse getSupplierDetail(Long supplierId, int months) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

        return SupplierDetailResponse.of(
                supplier,
                dataProvider.findSuppliedParts(supplierId),
                dataProvider.findSubmissions(supplierId, months),
                dataProvider.findAlerts(supplierId, months),
                dataProvider.findFeedbackHistories(supplierId, months)
        );
    }

    /**
     * 협력 상태 전이. INACTIVE 로 내릴 때 사유를 필수로 받는다 —
     * ERD 무결성 규칙 2번이자, 사유 없이 끊긴 업체는 나중에 왜 제외됐는지 설명할 수 없기 때문이다.
     */
    private void applyStatusTransition(Supplier supplier, SupplierUpdateRequest request) {
        SupplierStatus status = SupplierStatus.from(request.status());
        if (status == null) {
            throw new SupplierException(SupplierErrorCode.INVALID_STATUS);
        }
        if (status == SupplierStatus.INACTIVE) {
            if (request.statusReason() == null || request.statusReason().isBlank()) {
                throw new SupplierException(
                        SupplierErrorCode.INVALID_REQUEST,
                        "협력 끊김으로 전환하려면 statusReason 이 필요합니다",
                        Map.of("fieldErrors", Map.of("statusReason", "협력 끊김 사유는 필수입니다")));
            }
            supplier.deactivate(request.statusReason());
        } else {
            supplier.activate();
        }
    }

    private Page<Supplier> findPage(SupplierSearchCondition condition, Pageable pageable) {
        if (condition.submissionStatus() == null) {
            return supplierRepository.findAll(SupplierSpecifications.matches(
                    condition.search(), condition.country(), condition.status(), null), pageable);
        }

        Optional<Set<Long>> filtered = dataProvider.findSupplierIdsBySubmissionStatus(
                condition.submissionStatus(), condition.months());
        // 조회 경로가 없거나(empty Optional) 해당 업체가 없으면(empty Set) 결과는 0건이다.
        if (filtered.isEmpty() || filtered.get().isEmpty()) {
            return Page.empty(pageable);
        }
        return supplierRepository.findAll(SupplierSpecifications.matches(
                condition.search(), condition.country(), condition.status(), filtered.get()), pageable);
    }
}
