package com.skala.cbam.supplier.service.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.FeedbackHistorySummary;
import com.skala.cbam.supplier.dto.SupplierDetailResponse.PartSummary;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 협력업체 상세(№4)에서 <b>남의 도메인이 소유한 값</b>이 실제 데이터로 나오는지 본다.
 *
 * <p>{@link AvailableDomainDataProvider} 는 패키지 순환을 만들지 않으려고 부품·피드백 패키지를
 * import 하지 않고 JPQL 문자열로 읽는다. 그래서 <b>그쪽에서 엔티티나 필드 이름을 바꾸면
 * 컴파일이 잡아 주지 못한다.</b> 이 테스트가 그 자리를 지킨다.
 */
@SpringBootTest
@Transactional
@DisplayName("협력업체 상세 — 다른 도메인 값 채우기")
class AvailableDomainDataProviderTest {

    private static final String SUPPLIERS = "/api/v1/suppliers";
    private static final String PARTS = "/api/v1/parts";
    private static final String DRAFTS = "/api/v1/feedback-drafts";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SupplierRelatedDataProvider dataProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private long createSupplier(String name, String bizNo, String email) throws Exception {
        String body = """
                {
                  "companyName": "%s", "businessRegistrationNumber": "%s", "country": "KR",
                  "contactName": "김철수", "contactEmail": "%s"
                }""".formatted(name, bizNo, email);
        String res = mockMvc.perform(post(SUPPLIERS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    /** 남의 엔티티를 직접 세우지 않고 그 도메인의 공개 API 로 만든다. */
    private void createPart(String code, String name, String cnCode, Long... supplierIds) throws Exception {
        String ids = String.join(",", Arrays.stream(supplierIds).map(String::valueOf).toList());
        mockMvc.perform(post(PARTS).contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "partCode": "%s", "partName": "%s", "cnCode": "%s",
                  "unit": "TON", "benchmarkFactor": 2.1000, "supplierIds": [%s]
                }""".formatted(code, name, cnCode, ids)))
                .andExpect(status().is2xxSuccessful());
    }

    private String thisMonth() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    @Nested
    @DisplayName("공급 부품 — 부품 도메인")
    class SuppliedParts {

        @Test
        @DisplayName("공급 부품이 상세 응답에 실제로 담긴다 (빈 배열이 아니다)")
        void detailCarriesSuppliedParts() throws Exception {
            long supplierId = createSupplier("부품보유사", "701-81-70001", "parts-owner@example.test");
            createPart("P-7001", "열연강판", "72081000", supplierId);
            createPart("P-7002", "알루미늄프레임", "76042990", supplierId);

            mockMvc.perform(get(SUPPLIERS + "/" + supplierId).param("months", "12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parts.length()").value(2))
                    // 명세 №4 의 이름 그대로여야 한다 — partId · partCode · partName · cnCode
                    .andExpect(jsonPath("$.parts[0].partId").isNumber())
                    .andExpect(jsonPath("$.parts[0].partCode").value("P-7001"))
                    .andExpect(jsonPath("$.parts[0].partName").value("열연강판"))
                    .andExpect(jsonPath("$.parts[0].cnCode").value("72081000"));
        }

        /** 「되는 것」만 보면 절반이다. 남의 부품이 섞이면 화면은 <b>오류 없이 틀린 목록</b>을 그린다. */
        @Test
        @DisplayName("다른 협력업체의 부품은 섞이지 않는다")
        void doesNotLeakOtherSuppliersParts() throws Exception {
            long mine = createSupplier("내회사", "701-81-70002", "mine@example.test");
            long other = createSupplier("남의회사", "701-81-70003", "other@example.test");
            createPart("P-7011", "내부품", "72081000", mine);
            createPart("P-7012", "남의부품", "72089000", other);

            mockMvc.perform(get(SUPPLIERS + "/" + mine).param("months", "12"))
                    .andExpect(jsonPath("$.parts.length()").value(1))
                    .andExpect(jsonPath("$.parts[0].partCode").value("P-7011"));
        }

        /** 같은 부품을 여러 협력사가 공급한다(명세 №5~№8 의 supplierIds 배열). */
        @Test
        @DisplayName("한 부품을 두 협력사가 공급하면 양쪽 상세에 모두 나온다")
        void sharedPartAppearsForBothSuppliers() throws Exception {
            long a = createSupplier("공급사A", "701-81-70004", "a@example.test");
            long b = createSupplier("공급사B", "701-81-70005", "b@example.test");
            createPart("P-7021", "공용부품", "72081000", a, b);

            for (long id : List.of(a, b)) {
                mockMvc.perform(get(SUPPLIERS + "/" + id).param("months", "12"))
                        .andExpect(jsonPath("$.parts.length()").value(1))
                        .andExpect(jsonPath("$.parts[0].partCode").value("P-7021"));
            }
        }

        @Test
        @DisplayName("공급 부품이 없으면 빈 배열이고 키는 남는다")
        void emptyWhenNoParts() throws Exception {
            long supplierId = createSupplier("부품없음", "701-81-70006", "nopart@example.test");
            mockMvc.perform(get(SUPPLIERS + "/" + supplierId).param("months", "12"))
                    .andExpect(jsonPath("$.parts").isArray())
                    .andExpect(jsonPath("$.parts.length()").value(0));
        }

        /** JPQL 이 문자열이라 부품 쪽 이름이 바뀌면 런타임에야 터진다. 여기서 먼저 드러나게 한다. */
        @Test
        @DisplayName("JPQL 이 부품 엔티티 이름과 맞는다 (Part · partCode · partName · cnCode · supplierIds)")
        void jpqlMatchesPartEntityNames() {
            List<PartSummary> parts = dataProvider.findSuppliedParts(-1L);
            assertThat(parts).as("이름이 어긋나면 쿼리 파싱에서 예외가 난다").isEmpty();
        }
    }

    @Nested
    @DisplayName("피드백 발송 이력 — 피드백 도메인")
    class FeedbackHistories {

        /** 초안을 만들면 그 협력사의 피드백 건이 생긴다(42·43번). 상세에 그대로 보여야 한다. */
        @Test
        @DisplayName("피드백 건이 상세 응답에 실제로 담긴다 (빈 배열이 아니다)")
        void detailCarriesFeedbackHistories() throws Exception {
            long supplierId = createSupplier("피드백대상", "702-81-70011", "fb@example.test");
            createPart("P-7101", "피드백부품", "72081000", supplierId);
            createDraft(supplierId);

            mockMvc.perform(get(SUPPLIERS + "/" + supplierId).param("months", "12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.feedbackHistories.length()").value(1))
                    // 명세 №4 의 이름 그대로 — draftId · type · sentAt · status
                    .andExpect(jsonPath("$.feedbackHistories[0].draftId").isNumber())
                    .andExpect(jsonPath("$.feedbackHistories[0].type").exists())
                    // 아직 보내지 않았다. 「보냈다」로 지어내지 않고 PENDING · sentAt=null 이어야 한다
                    .andExpect(jsonPath("$.feedbackHistories[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.feedbackHistories[0].sentAt").doesNotExist());
        }

        @Test
        @DisplayName("다른 협력업체의 피드백은 섞이지 않는다")
        void doesNotLeakOtherSuppliersFeedback() throws Exception {
            long mine = createSupplier("내피드백", "702-81-70012", "fb-mine@example.test");
            long other = createSupplier("남의피드백", "702-81-70013", "fb-other@example.test");
            createPart("P-7111", "내피드백부품", "72081000", mine);
            createPart("P-7112", "남의피드백부품", "72089000", other);
            createDraft(mine);
            createDraft(other);

            mockMvc.perform(get(SUPPLIERS + "/" + mine).param("months", "12"))
                    .andExpect(jsonPath("$.feedbackHistories.length()").value(1));
        }

        @Test
        @DisplayName("피드백이 없으면 빈 배열이고 키는 남는다")
        void emptyWhenNoFeedback() throws Exception {
            long supplierId = createSupplier("피드백없음", "702-81-70014", "nofb@example.test");
            mockMvc.perform(get(SUPPLIERS + "/" + supplierId).param("months", "12"))
                    .andExpect(jsonPath("$.feedbackHistories").isArray())
                    .andExpect(jsonPath("$.feedbackHistories.length()").value(0));
        }

        /** JPQL 이 문자열이라 피드백 쪽 이름이 바뀌면 런타임에야 터진다. */
        @Test
        @DisplayName("JPQL 이 피드백·Task 엔티티 이름과 맞는다 (Feedback · supplier · reportingMonth · Task · SEND_FEEDBACK)")
        void jpqlMatchesFeedbackEntityNames() {
            List<FeedbackHistorySummary> histories = dataProvider.findFeedbackHistories(-1L, 12);
            assertThat(histories).as("이름이 어긋나면 쿼리 파싱에서 예외가 난다").isEmpty();
        }

        private void createDraft(long supplierId) throws Exception {
            mockMvc.perform(post(DRAFTS).contentType(MediaType.APPLICATION_JSON).content("""
                    {
                      "reportingMonth": "%s",
                      "targets": [{ "supplierId": %d }],
                      "style": "FORMAL"
                    }""".formatted(thisMonth(), supplierId)))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Nested
    @DisplayName("아직 없는 도메인 — 채운 척하지 않는다")
    class NotYetAvailable {

        @Test
        @DisplayName("적격 필터는 빈 Set 이 아니라 «조회 경로 없음» 을 알린다")
        void submissionStatusFilterReportsNoLookupPath() {
            assertThat(dataProvider.findSupplierIdsBySubmissionStatus("QUALIFIED", 12))
                    .as("빈 Set 이면 서비스가 「그런 업체가 없다」로 오해해 조용히 0건을 낸다")
                    .isEmpty();
        }

        @Test
        @DisplayName("제출·경보는 도메인이 없어 비어 있다 (0으로 채우지 않는다)")
        void submissionAndAlertStayEmpty() {
            assertThat(dataProvider.findSubmissions(1L, 12)).isEmpty();
            assertThat(dataProvider.findAlerts(1L, 12)).isEmpty();
            assertThat(dataProvider.findMonthlyStatuses(List.of(1L), 12)).isEmpty();
        }
    }
}
