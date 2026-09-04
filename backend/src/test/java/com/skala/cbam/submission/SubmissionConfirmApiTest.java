package com.skala.cbam.submission;

import com.jayway.jsonpath.JsonPath;
import com.skala.cbam.parts.entity.PartSupplierStatus;
import com.skala.cbam.parts.repository.PartSupplierRepository;
import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.repository.SubmissionRepository;
import com.skala.cbam.supplier.repository.SupplierRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 31번 확정을 <b>진짜 어댑터로</b> 통과시키는 테스트 (API 명세 22행).
 *
 * <p>SubmissionServiceTest 는 확정 성공 경로를 Fake 프로바이더(연도를 항상 2026 으로 주는)로만
 * 검증한다. 그래서 실제 어댑터가 연도를 못 구해 확정이 영영 막히던 동안에도 그 테스트는 통과했다.
 * 이 테스트는 부품 등록부터 확정까지 실제 구성으로 지나가, 같은 결함이 다시 들어오면 깨진다.
 */
@SpringBootTest
@Transactional
class SubmissionConfirmApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PartSupplierRepository partSupplierRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 배출계수_연도가_있는_부품의_적격_제출은_확정된다() throws Exception {
        long supplierId = createSupplier("111-11-11111", "a@example.test");
        long partId = createPart(supplierId, "P-CONF-1", "열연강판", 2026);
        Submission submission = saveQualifiedSubmission(supplierId, partSupplierId(partId, supplierId));

        mockMvc.perform(post("/api/v1/submissions/{id}/confirm", submission.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator-Id", "이과장"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedBy").value("이과장"))
                // 확정 시점의 스냅샷 — 확정한 날짜의 연도가 아니라 부품에 설정된 연도여야 한다
                .andExpect(jsonPath("$.calculatedEmission.appliedFactorYear").value(2026))
                .andExpect(jsonPath("$.calculatedEmission.frozen").value(true))
                // (1500 + 400) / 1000
                .andExpect(jsonPath("$.calculatedEmission.emissionIntensity").value(1.9000));
    }

    @Test
    void 부품에_배출계수_연도가_없으면_확정을_막는다() throws Exception {
        long supplierId = createSupplier("222-22-22222", "b@example.test");
        long partId = createPart(supplierId, "P-CONF-2", "연도 없는 부품", null);
        Submission submission = saveQualifiedSubmission(supplierId, partSupplierId(partId, supplierId));

        // 되돌릴 수 없는 스냅샷에 출처 없는 값을 넣지 않는다 — 채우지 말고 막는다
        mockMvc.perform(post("/api/v1/submissions/{id}/confirm", submission.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator-Id", "이과장"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BENCHMARK_FACTOR_YEAR_UNKNOWN"));
    }

    private long createSupplier(String businessNumber, String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"한빛스틸","businessRegistrationNumber":"%s","country":"KR",
                                 "contactName":"김담당","contactEmail":"%s"}
                                """.formatted(businessNumber, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long createPart(long supplierId, String code, String name, Integer factorYear)
            throws Exception {
        String yearField = factorYear == null ? "" : "\"benchmarkFactorYear\":%d,".formatted(factorYear);
        String response = mockMvc.perform(post("/api/v1/parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partCode":"%s","partName":"%s","cnCode":"72081000","unit":"TON",
                                 "benchmarkFactor":2.1000,%s"supplierIds":[%d]}
                                """.formatted(code, name, yearField, supplierId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long partSupplierId(long partId, long supplierId) {
        return partSupplierRepository.findAllActiveRelations(
                        List.of(partId), List.of(supplierId), PartSupplierStatus.ACTIVE)
                .get(0).getId();
    }

    private Submission saveQualifiedSubmission(long supplierId, long partSupplierId) {
        Submission saved = submissionRepository.save(Submission.builder()
                .supplier(supplierRepository.findById(supplierId).orElseThrow())
                .partSupplierId(partSupplierId)
                .reportingMonth(YearMonth.of(2026, 9).atDay(1))
                .documentType("XLSX")
                .productionQuantityTon(new BigDecimal("1000.000"))
                .directEmissionTco2e(new BigDecimal("1500.000"))
                .indirectEmissionTco2e(new BigDecimal("400.000"))
                .defaultValueRatio(new BigDecimal("0.0000"))
                .status(SubmissionStatus.REVIEW_PENDING)
                .judgement(Judgement.QUALIFIED)
                .submittedAt(OffsetDateTime.now())
                .build());
        submissionRepository.flush();
        return saved;
    }
}
