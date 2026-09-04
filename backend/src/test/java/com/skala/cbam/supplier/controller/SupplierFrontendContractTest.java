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
 * 협력업체 API 가 <b>화면이 실제로 보내는 요청·읽는 필드</b>를 그대로 만족하는지 고정한다.
 *
 * <p>여기 적힌 파라미터 이름과 응답 필드 이름은 상상이 아니라
 * {@code frontend/src/api/index.js} 의 실서버 구현과 {@code frontend/src/api/shapes.js} 의
 * 변환기에서 그대로 옮긴 것이다. 화면은 이 이름들로만 값을 찾는다 —
 * 하나라도 바뀌면 화면은 <b>오류 없이 빈 값을 그린다.</b> 그게 가장 늦게 발견되는 고장이다.
 *
 * <p><b>왜 별도 파일인가:</b> {@link SupplierApiTest} 는 「명세대로 동작하는가」를 본다.
 * 이 파일은 「그 동작이 화면과 이어지는가」를 본다. 명세를 지켜도 필드 이름이 어긋나면
 * 화면은 붙지 않으므로, 깨졌을 때 어느 쪽 계약이 깨진 것인지 파일 이름으로 갈리게 둔다.
 *
 * <p><b>이 파일은 백엔드만 고친다.</b> 프론트 코드는 남의 영역이라 건드리지 않는다.
 */
@SpringBootTest
@Transactional
@DisplayName("협력업체 API — 화면 연결 계약")
class SupplierFrontendContractTest {

    private static final String BASE = "/api/v1/suppliers";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** 화면의 등록 폼이 보내는 본문 그대로 (shapes.js supplierToServer). */
    private String createBody(String companyName, String bizNo, String email) {
        return """
                {
                  "companyName": "%s",
                  "businessRegistrationNumber": "%s",
                  "country": "KR",
                  "contactName": "김철수",
                  "contactEmail": "%s",
                  "phone": "02-000-0000"
                }""".formatted(companyName, bizNo, email);
    }

    private long createSupplier() throws Exception {
        String body = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("계약검증", "111-11-11111", "contract@example.test")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Nested
    @DisplayName("№3 목록 — Suppliers.list()")
    class ListContract {

        /**
         * 화면이 실제로 보내는 쿼리다(index.js).
         * <pre>{ search, country, status, submissionStatus, months: 12, page, size, sort }</pre>
         * 이름이 하나라도 다르면 스프링이 조용히 무시하고 <b>필터가 안 걸린 전체 목록</b>이 나간다.
         */
        @Test
        @DisplayName("화면이 보내는 8개 파라미터를 그대로 받는다 (이름이 다르면 필터가 조용히 풀린다)")
        void acceptsExactQueryParametersTheScreenSends() throws Exception {
            createSupplier();
            mockMvc.perform(get(BASE)
                            .param("search", "계약")
                            .param("country", "KR")
                            .param("status", "ACTIVE")
                            .param("months", "12")
                            .param("page", "0")
                            .param("size", "100")
                            .param("sort", "companyName,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        /** submissionStatus 는 값이 있을 때만 나간다 — 제출 도메인이 없어 결과는 0건이어야 한다. */
        @Test
        @DisplayName("submissionStatus 를 보내면 200 과 빈 목록을 준다 (전체를 돌려주지 않는다)")
        void submissionStatusFilterReturnsEmptyRatherThanEverything() throws Exception {
            createSupplier();
            mockMvc.perform(get(BASE).param("submissionStatus", "QUALIFIED").param("months", "12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        /**
         * shapes.js 의 supplierRowFromServer 가 찾는 이름들이다.
         * monthlyStatus 가 없으면 화면은 12칸 스트립을 만들지 못한다.
         */
        @Test
        @DisplayName("행이 화면이 읽는 이름을 그대로 준다 — id · companyName · country · status · monthlyStatus")
        void rowCarriesFieldNamesTheMapperReads() throws Exception {
            createSupplier();
            mockMvc.perform(get(BASE).param("months", "12"))
                    .andExpect(jsonPath("$.content[0].id").exists())
                    .andExpect(jsonPath("$.content[0].companyName").value("계약검증"))
                    .andExpect(jsonPath("$.content[0].country").value("KR"))
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                    // 값이 없어도 키를 생략하지 않는다(공통 규약 9항) — 생략하면 화면이 undefined 를 만난다
                    .andExpect(jsonPath("$.content[0].monthlyStatus").isArray());
        }

        /** 화면은 전량을 받아 브라우저에서 거른다(ADR-0009). 봉투 다섯 키가 없으면 페이지 계산이 깨진다. */
        @Test
        @DisplayName("페이징 봉투 다섯 키를 준다 — content · page · size · totalElements · totalPages")
        void carriesPagingEnvelope() throws Exception {
            mockMvc.perform(get(BASE))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.page").exists())
                    .andExpect(jsonPath("$.size").exists())
                    .andExpect(jsonPath("$.totalElements").exists())
                    .andExpect(jsonPath("$.totalPages").exists());
        }
    }

    @Nested
    @DisplayName("№4 상세 — Suppliers.get()")
    class DetailContract {

        /** 화면은 {@code GET /suppliers/{id}?months=12} 하나만 부른다. */
        @Test
        @DisplayName("months=12 로 부르면 화면이 읽는 이름을 모두 준다")
        void detailCarriesFieldNamesTheMapperReads() throws Exception {
            long id = createSupplier();
            mockMvc.perform(get(BASE + "/" + id).param("months", "12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("계약검증"))
                    .andExpect(jsonPath("$.businessRegistrationNumber").value("111-11-11111"))
                    .andExpect(jsonPath("$.contactName").value("김철수"))
                    .andExpect(jsonPath("$.contactEmail").value("contract@example.test"))
                    .andExpect(jsonPath("$.phone").value("02-000-0000"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    // 아래 넷은 다른 도메인이 채운다. 지금은 비어 있어도 **키는 있어야** 화면이 .length 를 읽는다
                    .andExpect(jsonPath("$.parts").isArray())
                    .andExpect(jsonPath("$.submissions").isArray())
                    .andExpect(jsonPath("$.alerts").isArray())
                    .andExpect(jsonPath("$.feedbackHistories").isArray());
        }

        /** 선택 입력인 phone 이 비어도 키를 빼지 않는다 — 화면은 {@code s.phone ?? '—'} 로 읽는다. */
        @Test
        @DisplayName("phone 이 없으면 키를 빼지 않고 null 로 준다")
        void keepsNullPhoneKey() throws Exception {
            String body = """
                    {
                      "companyName": "전화없음",
                      "businessRegistrationNumber": "222-22-22222",
                      "country": "KR",
                      "contactName": "박담당",
                      "contactEmail": "nophone@example.test"
                    }""";
            String created = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getContentAsString();
            long id = ((Number) JsonPath.read(created, "$.id")).longValue();

            /* jsonPath 로는 「키가 없다」와 「키가 있고 null 이다」를 가릴 수 없다.
               규약 9항이 요구하는 것은 후자이므로 본문 문자열을 직접 본다 —
               키가 빠지면 화면의 s.phone 은 undefined 가 되고 «—» 대신 빈칸이 그려진다. */
            String detail = mockMvc.perform(get(BASE + "/" + id).param("months", "12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contactName").value("박담당"))
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(detail)
                    .as("phone 키가 null 값으로 남아 있어야 한다 (생략하면 안 된다)")
                    .contains("\"phone\":null");
        }
    }

    @Nested
    @DisplayName("№1 등록 — Suppliers.create()")
    class CreateContract {

        /**
         * 화면은 등록 응답을 <b>상세 변환기</b>에 그대로 통과시킨다
         * ({@code supplierDetailFromServer(await http('POST', ...))}).
         * 그래서 등록 응답에도 id · companyName · status 가 있어야 화면이 곧바로 목록으로 넘어간다.
         */
        @Test
        @DisplayName("등록 응답이 상세 변환기가 읽는 이름을 담는다")
        void createResponseFeedsDetailMapper() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("등록계약", "333-33-33333", "create@example.test")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.companyName").value("등록계약"))
                    .andExpect(jsonPath("$.businessRegistrationNumber").value("333-33-33333"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        /**
         * 화면은 오류가 <b>어느 칸</b>인지 알아야 붉게 칠한다.
         * client.js 는 {@code details.fieldErrors} 의 키를 폼 이름으로 바꿔 쓴다 —
         * 그 키가 없으면 어느 칸이 문제인지 화면이 표시하지 못한다.
         */
        @Test
        @DisplayName("형식 오류는 details.fieldErrors 에 서버 필드 이름을 담는다 (화면이 칸을 짚는 근거)")
        void validationErrorNamesTheField() throws Exception {
            String body = """
                    {
                      "companyName": "형식오류",
                      "businessRegistrationNumber": "444-44-44444",
                      "country": "KR",
                      "contactName": "김철수",
                      "contactEmail": "이건이메일이아니다"
                    }""";
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.details.fieldErrors.contactEmail").exists());
        }
    }

    /**
     * <b>화면은 아직 이 경로로 오지 않는다.</b> frontend/src/api/index.js 의 {@code Suppliers.update} 에는
     * 실서버 구현(request 의 세 번째 인자)이 없어 목으로 고정돼 있고, 협력 끊김은 명세에 없는
     * {@code POST /suppliers/{id}/deactivate} 를 부른다.
     *
     * <p>그건 프론트가 고칠 일이고 백엔드가 뚫을 수 있는 경로가 아니다.
     * 대신 <b>프론트가 붙이러 왔을 때 백엔드가 준비돼 있는지</b>를 여기서 고정해 둔다 —
     * 화면이 보낼 본문 모양 그대로 넣어 본다.
     */
    @Nested
    @DisplayName("№2 수정 · 협력 끊김 — 화면이 붙으면 쓸 계약")
    class UpdateContract {

        @Test
        @DisplayName("화면의 담당자 수정 폼 세 칸을 그대로 받는다")
        void acceptsContactEditForm() throws Exception {
            long id = createSupplier();
            String body = """
                    { "contactName": "이영희", "contactEmail": "new@example.test", "phone": "010-1111-2222" }""";
            mockMvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contactName").value("이영희"))
                    .andExpect(jsonPath("$.contactEmail").value("new@example.test"))
                    .andExpect(jsonPath("$.phone").value("010-1111-2222"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        /**
         * 화면 상세는 PATCH 결과를 기존 값에 <b>덮어쓴다</b>
         * ({@code s.value = { ...s.value, ...(await Suppliers.update(...)) }}).
         * 응답에 companyName 이 들어 있지 않아야 그 방식이 안전하다 — 들어 있는데 값이 비면 업체명이 지워진다.
         */
        @Test
        @DisplayName("수정 응답에 companyName 을 담지 않는다 (화면이 덮어쓰기로 병합하므로)")
        void updateResponseOmitsUnchangedIdentityFields() throws Exception {
            long id = createSupplier();
            mockMvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"contactName\": \"이영희\" }"))
                    .andExpect(jsonPath("$.companyName").doesNotExist())
                    .andExpect(jsonPath("$.businessRegistrationNumber").doesNotExist())
                    .andExpect(jsonPath("$.country").doesNotExist());
        }

        /** 협력 끊김은 PATCH 하나로 된다 — 별도 경로가 필요하지 않다는 근거다. */
        @Test
        @DisplayName("협력 끊김은 PATCH 로 되고 제외·보존 건수를 함께 준다")
        void deactivatesThroughPatch() throws Exception {
            long id = createSupplier();
            mockMvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"status\": \"INACTIVE\", \"statusReason\": \"계약 종료\" }"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INACTIVE"))
                    .andExpect(jsonPath("$.excludedSubmissionCount").exists())
                    .andExpect(jsonPath("$.preservedSubmissionCount").exists());
        }

        @Test
        @DisplayName("사유 없는 협력 끊김은 막고 statusReason 을 짚어 준다")
        void blocksDeactivationWithoutReason() throws Exception {
            long id = createSupplier();
            mockMvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"status\": \"INACTIVE\" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.fieldErrors.statusReason").exists());
        }

        /** 끊은 뒤 다시 조회해도 데이터가 남아 있어야 한다 — 요구사항 6번. */
        @Test
        @DisplayName("협력 끊김 뒤에도 상세가 그대로 조회된다 (삭제하지 않는다)")
        void keepsSupplierAfterDeactivation() throws Exception {
            long id = createSupplier();
            mockMvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"status\": \"INACTIVE\", \"statusReason\": \"계약 종료\" }"));
            mockMvc.perform(get(BASE + "/" + id).param("months", "12"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("계약검증"))
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }
    }
}
