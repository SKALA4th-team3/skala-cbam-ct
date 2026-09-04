package com.skala.cbam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** H2에서 기본 목록 조회가 실행되는지 확인한다 (3번, 15번). */
@SpringBootTest
class H2ListApiTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void 기본_협력업체와_메일_목록은_H2에서_500없이_조회된다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/api/v1/suppliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/mail-receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
