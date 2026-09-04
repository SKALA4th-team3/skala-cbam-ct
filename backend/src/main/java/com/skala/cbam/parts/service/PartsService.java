package com.skala.cbam.parts.service;

import com.skala.cbam.parts.dto.PageResponse;
import com.skala.cbam.parts.dto.PartCreateRequest;
import com.skala.cbam.parts.dto.PartDetailResponse;
import com.skala.cbam.parts.dto.PartResponse;
import com.skala.cbam.parts.dto.PartSummaryResponse;
import com.skala.cbam.parts.dto.PartUpdateRequest;
import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartUnit;
import com.skala.cbam.parts.exception.PartBusinessException;
import com.skala.cbam.parts.exception.PartErrorCode;
import com.skala.cbam.parts.repository.PartSpecifications;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 부품 기준정보 서비스 (요구사항 7~10번).
 *
 * <p><b>협력업체 의존은 parts → supplier 한 방향, 읽기 전용이다.</b> 부품은 공급 협력업체를
 * id 로만 들고 있으므로(엔티티 주석 참고) 이름을 채우려면 협력업체를 읽어야 한다.
 * 반대 방향(협력업체 상세의 공급 부품 목록)은 협력업체 쪽이 뚫어 둔
 * {@code SupplierRelatedDataProvider} 로 붙는다 — <b>여기서 그쪽을 부르지 않는다.</b>
 * 양방향으로 부르기 시작하면 순환이 생긴다.
 */
@Service
@Transactional(readOnly = true)
public class PartsService {

    private static final Pattern CN_CODE_PATTERN = Pattern.compile("\\d{8}");

    private final PartsRepository partsRepository;
    private final SupplierRepository supplierRepository;

    public PartsService(PartsRepository partsRepository, SupplierRepository supplierRepository) {
        this.partsRepository = partsRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional
    public PartResponse create(PartCreateRequest request) {
        validateCnCode(request.cnCode());
        PartUnit unit = parseUnit(request.unit());
        validateBenchmarkFactor(request.benchmarkFactor());
        validateSuppliersExist(request.supplierIds());

        if (partsRepository.existsByPartCode(request.partCode())) {
            throw new PartBusinessException(PartErrorCode.DUPLICATE_PART_CODE);
        }
        if (partsRepository.existsByPartName(request.partName())) {
            throw new PartBusinessException(PartErrorCode.DUPLICATE_PART_NAME);
        }

        Part part = new Part(
                request.partCode(),
                request.partName(),
                request.cnCode(),
                unit,
                request.benchmarkFactor(),
                request.benchmarkFactorYear(),
                request.supplierIds()
        );
        return PartResponse.from(partsRepository.save(part));
    }

    @Transactional
    public PartResponse update(Long partId, PartUpdateRequest request) {
        Part part = getOrThrow(partId);

        if (request.cnCode() != null) {
            validateCnCode(request.cnCode());
        }
        PartUnit unit = request.unit() != null ? parseUnit(request.unit()) : null;
        if (request.benchmarkFactor() != null) {
            validateBenchmarkFactor(request.benchmarkFactor());
        }
        validateSuppliersExist(request.supplierIds());
        if (request.partName() != null && partsRepository.existsByPartNameAndIdNot(request.partName(), partId)) {
            throw new PartBusinessException(PartErrorCode.DUPLICATE_PART_NAME);
        }

        part.update(request.partName(), request.cnCode(), unit, request.benchmarkFactor(),
                request.benchmarkFactorYear(), request.supplierIds());
        return PartResponse.from(part);
    }

    public PageResponse<PartSummaryResponse> list(String search, Long supplierId, String cnCode, Pageable pageable) {
        var page = partsRepository.findAll(PartSpecifications.search(search, supplierId, cnCode), pageable);

        // 한 페이지분 협력업체 이름을 한 번에 읽는다. 행마다 조회하면 N+1 이 된다.
        Map<Long, String> names = supplierNames(page.getContent().stream()
                .flatMap(part -> part.getSupplierIds().stream())
                .collect(Collectors.toSet()));

        return PageResponse.from(page, part -> PartSummaryResponse.from(part, names));
    }

    public PartDetailResponse getDetail(Long partId) {
        Part part = getOrThrow(partId);
        return PartDetailResponse.from(part, supplierNames(part.getSupplierIds()));
    }

    private Part getOrThrow(Long partId) {
        return partsRepository.findById(partId)
                .orElseThrow(() -> new PartBusinessException(PartErrorCode.PART_NOT_FOUND));
    }

    /**
     * 공급 협력업체로 지정한 id 가 실제로 있는지 본다 (7번 · 8번).
     *
     * <p>없는 id 를 그대로 저장하면 부품 상세와 협력업체 상세가 서로 다른 말을 하게 되고,
     * 나중에 25번(부품 매핑)이 가리킬 곳 없는 관계 위에서 돌아간다.
     *
     * <p>null 은 "이 항목을 건드리지 않는다"(PATCH 부분 수정)이므로 통과시킨다.
     * 빈 집합은 "공급 협력업체를 비운다"이므로 검증할 대상이 없다.
     *
     * <p>협력 끊김(INACTIVE) 업체를 공급사로 둘 수 있는지는 <b>명세에 없다.</b>
     * 6번은 "마감 대상과 미제출 경보에서 제외"만 말하고 부품 등록은 말하지 않는다.
     * 임의로 막지 않고 존재 여부만 본다 — 막아야 한다면 팀이 정한 뒤에 여기에 붙인다.
     */
    private void validateSuppliersExist(Set<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return;
        }
        Set<Long> found = supplierRepository.findAllById(supplierIds).stream()
                .map(Supplier::getId)
                .collect(Collectors.toSet());
        List<Long> missing = supplierIds.stream()
                .filter(id -> !found.contains(id))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new PartBusinessException(
                    PartErrorCode.SUPPLIER_NOT_FOUND,
                    PartErrorCode.SUPPLIER_NOT_FOUND.getDefaultMessage(),
                    Map.of("missingSupplierIds", missing));
        }
    }

    /** id → 협력업체명. 조회되지 않은 id 는 키가 없다 — 호출부가 이름을 비운 채로 내보낸다. */
    private Map<Long, String> supplierNames(Collection<Long> supplierIds) {
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));
    }

    private void validateCnCode(String cnCode) {
        if (!CN_CODE_PATTERN.matcher(cnCode).matches()) {
            throw new PartBusinessException(PartErrorCode.INVALID_CN_CODE);
        }
    }

    private PartUnit parseUnit(String unit) {
        PartUnit parsed = PartUnit.from(unit);
        if (parsed == null) {
            throw new PartBusinessException(PartErrorCode.INVALID_UNIT);
        }
        return parsed;
    }

    private void validateBenchmarkFactor(BigDecimal benchmarkFactor) {
        if (benchmarkFactor.compareTo(BigDecimal.ZERO) < 0 || benchmarkFactor.scale() > 4) {
            throw new PartBusinessException(PartErrorCode.OUT_OF_RANGE);
        }
    }
}
