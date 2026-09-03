package com.skala.cbam.dashboard.controller;

import com.skala.cbam.common.exception.BusinessException;
import com.skala.cbam.common.exception.ErrorCode;
import com.skala.cbam.dashboard.dto.DashboardAlertsResponse;
import com.skala.cbam.dashboard.dto.DashboardResponse;
import com.skala.cbam.dashboard.dto.DashboardStatus;
import com.skala.cbam.dashboard.entity.SeverityCode;
import com.skala.cbam.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * CBAM-73 하위 CBAM-74·75·76 (38·39·40번).
 * API 명세서 v10 24·25행 그대로 구현. CBAM-77(41번)은 GET /api/v1/products 쪽으로
 * 흡수돼 있어(명세서 11행 비고) 여기 포함하지 않았다 — 팀 확인 중.
 *
 * X-Operator-Id 는 명세상 필수 헤더지만, 인증/인가 방식이 아직 팀 미결정 사항이라
 * 지금은 값만 받고 검증하지 않는다.
 */
@Tag(name = "Dashboard", description = "대시보드 조회 API (38·39·40번)")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Validated
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "대시보드 조회 (38·40번)",
            description = "현재 마감일 기준 협력업체별 상태와 건수, 월별 적격/부적격/미제출 비율과 심각도별 건수를 조회한다.")
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            @Parameter(description = "YYYY-MM, 기본값은 현재월")
            @RequestParam(required = false) String month,
            @Parameter(description = "QUALIFIED|UNQUALIFIED|NOT_SUBMITTED")
            @RequestParam(required = false) DashboardStatus status,
            @Parameter(description = "기본 severity,desc 로 고정 동작 — 다른 정렬은 아직 지원하지 않는다")
            @RequestParam(required = false) String sort
    ) {
        YearMonth targetMonth = parseMonth(month);
        return ResponseEntity.ok(dashboardService.getDashboard(targetMonth, status));
    }

    @Operation(summary = "경보 조회 (39번)",
            description = "마감 D-7 이내 미제출 업체와 판정 경보를 심각도 우선순위 순으로 조회한다.")
    @GetMapping("/alerts")
    public ResponseEntity<DashboardAlertsResponse> getAlerts(
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            @Parameter(description = "YYYY-MM, 기본값은 현재월")
            @RequestParam(required = false) String month,
            @Parameter(description = "HIGH|MEDIUM|LOW")
            @RequestParam(required = false) SeverityCode severity,
            @Parameter(description = "R1~R7")
            @RequestParam(required = false) String ruleId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page 는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size 는 1 이상이어야 합니다")
            @Max(value = 100, message = "size 는 100 이하여야 합니다") int size
    ) {
        YearMonth targetMonth = parseMonth(month);
        return ResponseEntity.ok(dashboardService.getAlerts(targetMonth, severity, ruleId, page, size));
    }

    /**
     * YYYY-MM 파싱. 값이 없으면 현재월이 기본값이다(40번 「현재 달을 기본값으로」).
     *
     * <p>YearMonth.parse 의 DateTimeParseException 을 그대로 두면 500 이 나간다.
     * 40번은 FE 가 월을 계속 바꿔 보는 화면이라, 잘못된 입력에 500 을 주면
     * FE 는 「서버가 죽었다」와 구분할 수 없다. 400 INVALID_PARAMETER 로 바꾼다(공통 규약 3항).
     */
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, Map.of(
                    "parameter", "month",
                    "rejectedValue", month,
                    "expected", "YYYY-MM"));
        }
    }
}
