package com.skala.cbam.products.controller;

import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@Transactional
class ProductsApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 완제품을_등록하면_201과_전체_등록결과를_반환한다() throws Exception {
        long supplierId = createSupplier();
        long partId = createPart(supplierId);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(partId, supplierId, "DE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productName").value("자동차 차체"))
                .andExpect(jsonPath("$.exportCountries[0]").value("DE"))
                .andExpect(jsonPath("$.parts[0].partId").value(partId))
                .andExpect(jsonPath("$.parts[0].partName").value("열연강판"))
                .andExpect(jsonPath("$.parts[0].supplierId").value(supplierId))
                .andExpect(jsonPath("$.parts[0].supplierName").value("대성금속"))
                .andExpect(jsonPath("$.parts[0].status").doesNotExist());
    }

    @Test
    void 존재하지_않는_부품이면_404로_막는다() throws Exception {
        long supplierId = createSupplier();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(9999L, supplierId, "DE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PART_NOT_FOUND"))
                .andExpect(jsonPath("$.details.missingPartIds[0]").value(9999L));
    }

    @Test
    void 연결되지_않은_부품과_협력업체이면_400으로_막는다() throws Exception {
        long supplierId = createSupplier();
        long otherSupplierId = createSupplier("999-99-99999", "other@example.test", "다른업체");
        long partId = createPart(supplierId);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(partId, otherSupplierId, "DE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PART_SUPPLIER_NOT_FOUND"));
    }

    @Test
    void EU_회원국이_아니면_400으로_막는다() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(1L, 1L, "KR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EU_COUNTRY"));
    }

    @Test
    void 필수값이_누락되면_400_INVALID_REQUEST로_막는다() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private long createSupplier() throws Exception {
        return createSupplier("123-45-67890", "supplier@example.test", "대성금속");
    }

    private long createSupplier(String businessNumber, String email, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s","businessRegistrationNumber":"%s","country":"KR",
                                 "contactName":"담당자","contactEmail":"%s"}
                                """.formatted(name, businessNumber, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long createPart(long supplierId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partCode":"P-9001","partName":"열연강판","cnCode":"72081000",
                                 "unit":"TON","benchmarkFactor":1.2500,"supplierIds":[%d]}
                                """.formatted(supplierId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private String productBody(long partId, long supplierId, String country) {
        return """
                {"productName":"자동차 차체","cnCode":"87082990","exportCountries":["%s"],
                 "annualExportTon":100.00,"parts":[{"partId":%d,"supplierId":%d,"inputQtyPerTon":1.250}]}
                """.formatted(country, partId, supplierId);
    }
}
