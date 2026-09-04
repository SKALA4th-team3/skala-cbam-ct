package com.skala.cbam.supplier.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
 * 협력업체 API(№1~№4 · 요구사항 1~6번) 통합 테스트.
 *
 * <p>「되는 것」만이 아니라 <b>막는 쪽</b>을 함께 확인한다 —
 * 명세의 조건문은 대부분 막는 쪽에 있다(중복 등록 불가, 허용값 아님, 사유 없는 끊김 등).
 */
@SpringBootTest
@Transactional
@DisplayName("협력업체 API")
class SupplierApiTest {

    private static final String BASE = "/api/v1/suppliers";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ────────────────────────────── №1 등록 ──────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/suppliers — 등록")
    class Create {

        @Test
        @DisplayName("등록하면 201 과 등록 전체 필드를 반환한다")
        void createsSupplier() throws Exception {
            mockMvc.perform(postSupplier(body("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.companyName").value("대한금속"))
                    .andExpect(jsonPath("$.businessRegistrationNumber").value("123-45-67890"))
                    .andExpect(jsonPath("$.country").value("KR"))
                    .andExpect(jsonPath("$.contactName").value("김철수"))
                    .andExpect(jsonPath("$.contactEmail").value("kim@daehan.co.kr"))
                    .andExpect(jsonPath("$.phone").value("02-1234-5678"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("사업자등록번호가 중복이면 409 DUPLICATE_BUSINESS_NUMBER 로 막는다")
        void rejectsDuplicateBusinessNumber() throws Exception {
            mockMvc.perform(postSupplier(body("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr")))
                    .andExpect(status().isCreated());

            mockMvc.perform(postSupplier(body("다른회사", "123-45-67890", "KR", "other@x.co.kr")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS_NUMBER"));
        }

        @Test
        @DisplayName("담당자 이메일이 중복이면 409 DUPLICATE_CONTACT_EMAIL 로 막는다")
        void rejectsDuplicateContactEmail() throws Exception {
            mockMvc.perform(postSupplier(body("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr")))
                    .andExpect(status().isCreated());

            mockMvc.perform(postSupplier(body("다른회사", "999-99-99999", "KR", "kim@daehan.co.kr")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_CONTACT_EMAIL"));
        }

        @Test
        @DisplayName("이메일 대소문자만 다른 중복도 막는다 (수신 메일 매칭 키이므로)")
        void rejectsDuplicateEmailIgnoringCase() throws Exception {
            mockMvc.perform(postSupplier(body("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr")))
                    .andExpect(status().isCreated());

            mockMvc.perform(postSupplier(body("다른회사", "999-99-99999", "KR", "KIM@Daehan.CO.KR")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_CONTACT_EMAIL"));
        }

        @Test
        @DisplayName("필수값이 빠지면 400 INVALID_REQUEST 로 막는다")
        void rejectsMissingRequiredFields() throws Exception {
            String noCompanyName = """
                    {"businessRegistrationNumber":"123-45-67890","country":"KR",
                     "contactName":"김철수","contactEmail":"kim@daehan.co.kr"}""";
            mockMvc.perform(postSupplier(noCompanyName))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.details.fieldErrors.companyName").exists());
        }

        @Test
        @DisplayName("이메일 형식이 틀리면 400 INVALID_REQUEST 로 막는다")
        void rejectsMalformedEmail() throws Exception {
            mockMvc.perform(postSupplier(body("대한금속", "123-45-67890", "KR", "not-an-email")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.details.fieldErrors.contactEmail").exists());
        }

        @Test
        @DisplayName("국가 코드가 alpha-2 가 아니면 400 INVALID_REQUEST 로 막는다")
        void rejectsMalformedCountry() throws Exception {
            mockMvc.perform(postSupplier(body("대한금속", "123-45-67890", "KOR", "kim@daehan.co.kr")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.fieldErrors.country").exists());
        }
    }

    // ──────────────────────── №2 수정 · 협력 끊김 ────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/suppliers/{id} — 수정 · 협력 끊김")
    class Update {

        @Test
        @DisplayName("담당자 정보를 수정하면 200 과 수정 결과를 반환한다")
        void updatesContact() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(patchSupplier(id, """
                            {"contactName":"박영희","contactEmail":"park@daehan.co.kr","phone":"02-9999-8888"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.contactName").value("박영희"))
                    .andExpect(jsonPath("$.contactEmail").value("park@daehan.co.kr"))
                    .andExpect(jsonPath("$.phone").value("02-9999-8888"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        @DisplayName("status 를 보내지 않으면 상태는 그대로 두고 정보만 고친다")
        void keepsStatusWhenNotSent() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");
            mockMvc.perform(patchSupplier(id, """
                    {"status":"INACTIVE","statusReason":"거래 종료"}"""))
                    .andExpect(status().isOk());

            mockMvc.perform(patchSupplier(id, """
                            {"contactName":"박영희"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }

        @Test
        @DisplayName("협력 끊김으로 전환하면 제외·보존 건수를 함께 반환한다")
        void deactivates() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(patchSupplier(id, """
                            {"status":"INACTIVE","statusReason":"거래 종료"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INACTIVE"))
                    .andExpect(jsonPath("$.excludedSubmissionCount").exists())
                    .andExpect(jsonPath("$.preservedSubmissionCount").exists());
        }

        @Test
        @DisplayName("사유 없이 협력 끊김으로 전환하면 400 으로 막는다")
        void rejectsDeactivationWithoutReason() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(patchSupplier(id, """
                            {"status":"INACTIVE"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.details.fieldErrors.statusReason").exists());
        }

        @Test
        @DisplayName("허용값이 아닌 status 는 400 INVALID_STATUS 로 막는다")
        void rejectsUnknownStatus() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(patchSupplier(id, """
                            {"status":"PENDING"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        }

        @Test
        @DisplayName("다른 업체가 쓰는 이메일로 바꾸면 409 DUPLICATE_CONTACT_EMAIL 로 막는다")
        void rejectsDuplicateEmailOnUpdate() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");
            createSupplier("한국철강", "999-99-99999", "KR", "lee@hanguk.co.kr");

            mockMvc.perform(patchSupplier(id, """
                            {"contactEmail":"lee@hanguk.co.kr"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_CONTACT_EMAIL"));
        }

        @Test
        @DisplayName("자기 이메일을 그대로 다시 보내는 것은 막지 않는다")
        void allowsSameEmailOnUpdate() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(patchSupplier(id, """
                            {"contactEmail":"kim@daehan.co.kr","contactName":"김철수2"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contactName").value("김철수2"));
        }

        @Test
        @DisplayName("없는 협력업체는 404 SUPPLIER_NOT_FOUND 로 막는다")
        void rejectsUnknownSupplier() throws Exception {
            mockMvc.perform(patchSupplier(999_999L, """
                            {"contactName":"박영희"}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
        }

        @Test
        @DisplayName("협력 끊김 뒤에도 기존 협력업체 데이터는 보존된다 (삭제하지 않는다)")
        void preservesSupplierAfterDeactivation() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");
            mockMvc.perform(patchSupplier(id, """
                    {"status":"INACTIVE","statusReason":"거래 종료"}"""))
                    .andExpect(status().isOk());

            mockMvc.perform(get(BASE + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businessRegistrationNumber").value("123-45-67890"))
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }
    }

    // ────────────────────────── №3 리스트 조회 ──────────────────────────

    @Nested
    @DisplayName("GET /api/v1/suppliers — 리스트 조회")
    class Search {

        @Test
        @DisplayName("공통 규약의 페이징 5개 키를 반환한다")
        void returnsPagingEnvelope() throws Exception {
            createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.content[0].companyName").value("대한금속"))
                    .andExpect(jsonPath("$.content[0].monthlyStatus").isArray());
        }

        @Test
        @DisplayName("업체명 검색은 부분 일치이고 대소문자를 가리지 않는다")
        void searchesByName() throws Exception {
            createSupplier("대한금속", "111-11-11111", "KR", "a@x.co.kr");
            createSupplier("Hanguk Steel", "222-22-22222", "KR", "b@x.co.kr");

            mockMvc.perform(get(BASE).param("search", "금속"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].companyName").value("대한금속"));

            mockMvc.perform(get(BASE).param("search", "hanguk"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("국가·협력상태 필터가 걸린다")
        void filtersByCountryAndStatus() throws Exception {
            createSupplier("대한금속", "111-11-11111", "KR", "a@x.co.kr");
            long jp = createSupplier("니혼공업", "222-22-22222", "JP", "b@x.co.kr");
            mockMvc.perform(patchSupplier(jp, """
                    {"status":"INACTIVE","statusReason":"거래 종료"}"""));

            mockMvc.perform(get(BASE).param("country", "JP"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].companyName").value("니혼공업"));

            mockMvc.perform(get(BASE).param("status", "ACTIVE"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].companyName").value("대한금속"));
        }

        @Test
        @DisplayName("companyName 오름·내림차순 정렬이 적용된다")
        void sortsByCompanyName() throws Exception {
            createSupplier("가나금속", "111-11-11111", "KR", "a@x.co.kr");
            createSupplier("하나금속", "222-22-22222", "KR", "b@x.co.kr");

            mockMvc.perform(get(BASE).param("sort", "companyName,asc"))
                    .andExpect(jsonPath("$.content[0].companyName").value("가나금속"));
            mockMvc.perform(get(BASE).param("sort", "companyName,desc"))
                    .andExpect(jsonPath("$.content[0].companyName").value("하나금속"));
        }

        @Test
        @DisplayName("지원하지 않는 정렬 키는 400 INVALID_PARAMETER 로 막는다 (조용히 무시하지 않는다)")
        void rejectsUnsupportedSortKey() throws Exception {
            mockMvc.perform(get(BASE).param("sort", "lastSubmittedAt,desc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.details.fieldErrors.sort").exists());
        }

        @Test
        @DisplayName("허용값이 아닌 status 필터는 400 INVALID_PARAMETER 로 막는다")
        void rejectsUnknownStatusFilter() throws Exception {
            mockMvc.perform(get(BASE).param("status", "PENDING"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }

        @Test
        @DisplayName("허용값이 아닌 submissionStatus 필터는 400 INVALID_PARAMETER 로 막는다")
        void rejectsUnknownSubmissionStatusFilter() throws Exception {
            mockMvc.perform(get(BASE).param("submissionStatus", "DONE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }

        @Test
        @DisplayName("적격 상태 필터는 판정 데이터 경로가 없으므로 빈 결과를 반환한다 (전체를 돌려주지 않는다)")
        void returnsEmptyWhenSubmissionFilterUnavailable() throws Exception {
            createSupplier("대한금속", "111-11-11111", "KR", "a@x.co.kr");

            mockMvc.perform(get(BASE).param("submissionStatus", "QUALIFIED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("page·size 범위 밖은 400 INVALID_PARAMETER 로 막는다")
        void rejectsInvalidPaging() throws Exception {
            mockMvc.perform(get(BASE).param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            mockMvc.perform(get(BASE).param("size", "0"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(BASE).param("size", "101"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("size 를 넘으면 다음 페이지로 나뉜다")
        void paginates() throws Exception {
            createSupplier("가나금속", "111-11-11111", "KR", "a@x.co.kr");
            createSupplier("나다금속", "222-22-22222", "KR", "b@x.co.kr");
            createSupplier("다라금속", "333-33-33333", "KR", "c@x.co.kr");

            mockMvc.perform(get(BASE).param("size", "2").param("page", "0"))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
            mockMvc.perform(get(BASE).param("size", "2").param("page", "1"))
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    // ────────────────────────── №4 상세 조회 ──────────────────────────

    @Nested
    @DisplayName("GET /api/v1/suppliers/{id} — 상세 조회")
    class Detail {

        @Test
        @DisplayName("기본정보를 반환하고 네 구간은 키를 유지한 채 빈 배열로 둔다")
        void returnsDetail() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(get(BASE + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.companyName").value("대한금속"))
                    .andExpect(jsonPath("$.businessRegistrationNumber").value("123-45-67890"))
                    .andExpect(jsonPath("$.country").value("KR"))
                    .andExpect(jsonPath("$.contactName").value("김철수"))
                    .andExpect(jsonPath("$.contactEmail").value("kim@daehan.co.kr"))
                    .andExpect(jsonPath("$.phone").value("02-1234-5678"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.parts").isArray())
                    .andExpect(jsonPath("$.parts.length()").value(0))
                    .andExpect(jsonPath("$.submissions").isArray())
                    .andExpect(jsonPath("$.alerts").isArray())
                    .andExpect(jsonPath("$.feedbackHistories").isArray());
        }

        @Test
        @DisplayName("없는 협력업체는 404 SUPPLIER_NOT_FOUND 로 막는다")
        void rejectsUnknownSupplier() throws Exception {
            mockMvc.perform(get(BASE + "/{id}", 999_999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"))
                    .andExpect(jsonPath("$.path").value(BASE + "/999999"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.details").exists());
        }

        @Test
        @DisplayName("months 범위 밖·형식 오류는 400 INVALID_PARAMETER 로 막는다")
        void rejectsInvalidMonths() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(get(BASE + "/{id}", id).param("months", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            mockMvc.perform(get(BASE + "/{id}", id).param("months", "25"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            mockMvc.perform(get(BASE + "/{id}", id).param("months", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }

        @Test
        @DisplayName("months 경계값 1 과 24 는 통과시킨다")
        void acceptsMonthsBoundaries() throws Exception {
            long id = createSupplier("대한금속", "123-45-67890", "KR", "kim@daehan.co.kr");

            mockMvc.perform(get(BASE + "/{id}", id).param("months", "1")).andExpect(status().isOk());
            mockMvc.perform(get(BASE + "/{id}", id).param("months", "24")).andExpect(status().isOk());
        }
    }

    // ────────────────────────── 공통 예외 처리 ──────────────────────────

    @Test
    @DisplayName("매핑 없는 경로는 404 로 남는다 (공통 예외 처리가 500 으로 바꾸지 않는다)")
    void keepsNotFoundForUnmappedPath() throws Exception {
        mockMvc.perform(get(BASE + "/1/no-such-section"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 로 남는다 (500 으로 바꾸지 않는다)")
    void keepsMethodNotAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(BASE + "/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("지원하지 않는 미디어 타입은 415 로 남는다 (500 으로 바꾸지 않는다)")
    void keepsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("전역 예외 처리기가 있어도 OpenAPI 문서를 생성한다")
    void servesOpenApiDocumentWithGlobalExceptionHandler() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"));
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private long createSupplier(String companyName, String businessNumber, String country, String email)
            throws Exception {
        String response = mockMvc.perform(postSupplier(body(companyName, businessNumber, country, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private String body(String companyName, String businessNumber, String country, String email) {
        return """
                {"companyName":"%s","businessRegistrationNumber":"%s","country":"%s",
                 "contactName":"김철수","contactEmail":"%s","phone":"02-1234-5678"}"""
                .formatted(companyName, businessNumber, country, email);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postSupplier(String body) {
        return post(BASE).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchSupplier(
            long id, String body) {
        return patch(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ────────────────────────── API 문서 (Swagger) ──────────────────────────

    @Nested
    @DisplayName("GET /v3/api-docs — API 문서")
    class ApiDocs {

        /**
         * springdoc 2.6.0 은 Spring Boot 4.1.1(Framework 7)에서 쉽게 깨진다.
         * @ControllerAdvice 빈이 하나라도 생기면 이 경로가 NoSuchMethodError 로 500 을 낸다
         * (SupplierApiExceptionHandling 주석 참고). 그 사고는 앱을 띄우기 전에는 드러나지 않고,
         * 드러나도 「Swagger 가 안 열리네」로 끝나 원인을 찾는 데 시간이 걸린다.
         */
        @Test
        @DisplayName("문서가 깨지지 않고 열린다 (@ControllerAdvice 가 들어오면 여기서 먼저 깨진다)")
        void servesApiDocs() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers']").exists());
        }

        /**
         * <b>막는 쪽이 문서에 남아 있는지 본다.</b>
         * 이 명세의 완료 조건은 대부분 「~인 경우에만」처럼 막는 쪽에 있는데,
         * 성공 응답만 문서화하면 화면 담당자는 되는 쪽만 보고 만들게 된다.
         * 어노테이션을 지우면 이 테스트가 먼저 깨진다.
         */
        @Test
        @DisplayName("등록은 201 과 400 · 409 를 함께 문서화한다")
        void documentsCreateFailures() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers'].post.responses.201").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers'].post.responses.400").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers'].post.responses.409").exists())
                    // 등록은 200 이 아니라 201 이다. 문서가 200 이라고 하면 화면이 잘못 분기한다
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers'].post.responses.200").doesNotExist());
        }

        @Test
        @DisplayName("수정은 400 · 404 · 409 를 함께 문서화한다 (사유 없는 협력 끊김 포함)")
        void documentsUpdateFailures() throws Exception {
            String path = "$.paths['/api/v1/suppliers/{supplierId}'].patch.responses";
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath(path + ".400").exists())
                    .andExpect(jsonPath(path + ".404").exists())
                    .andExpect(jsonPath(path + ".409").exists());
        }

        @Test
        @DisplayName("조회는 잘못된 파라미터의 400 을 문서화한다 (조용히 무시하지 않는다는 사실을 문서도 말한다)")
        void documentsQueryFailures() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers'].get.responses.400").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers/{supplierId}'].get.responses.400").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/suppliers/{supplierId}'].get.responses.404").exists());
        }
    }
}
