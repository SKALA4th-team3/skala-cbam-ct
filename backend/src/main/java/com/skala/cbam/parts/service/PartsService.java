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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class PartsService {

    private static final Pattern CN_CODE_PATTERN = Pattern.compile("\\d{8}");

    private final PartsRepository partsRepository;

    public PartsService(PartsRepository partsRepository) {
        this.partsRepository = partsRepository;
    }

    @Transactional
    public PartResponse create(PartCreateRequest request) {
        validateCnCode(request.cnCode());
        PartUnit unit = parseUnit(request.unit());
        validateBenchmarkFactor(request.benchmarkFactor());

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
        if (request.partName() != null && partsRepository.existsByPartNameAndIdNot(request.partName(), partId)) {
            throw new PartBusinessException(PartErrorCode.DUPLICATE_PART_NAME);
        }

        part.update(request.partName(), request.cnCode(), unit, request.benchmarkFactor(), request.supplierIds());
        return PartResponse.from(part);
    }

    public PageResponse<PartSummaryResponse> list(String search, Long supplierId, String cnCode, Pageable pageable) {
        var page = partsRepository.findAll(PartSpecifications.search(search, supplierId, cnCode), pageable);
        return PageResponse.from(page, PartSummaryResponse::from);
    }

    public PartDetailResponse getDetail(Long partId) {
        return PartDetailResponse.from(getOrThrow(partId));
    }

    private Part getOrThrow(Long partId) {
        return partsRepository.findById(partId)
                .orElseThrow(() -> new PartBusinessException(PartErrorCode.PART_NOT_FOUND));
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
