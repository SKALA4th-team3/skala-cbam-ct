package com.skala.cbam.mail;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.domain.MailReceiptStatus;
import com.skala.cbam.mail.repository.MailReceiptRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class MailApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MailReceiptRepository mailReceiptRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void 목록_정렬은_요청한_수신일시와_방향을_적용한다() throws Exception {
        MailReceipt older = saveReceipt("<older@mail.com>", OffsetDateTime.parse("2026-09-01T09:00:00+09:00"));
        MailReceipt newer = saveReceipt("<newer@mail.com>", OffsetDateTime.parse("2026-09-02T09:00:00+09:00"));

        mockMvc.perform(get("/api/v1/mail-receipts").param("sort", "receivedAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(older.getId()))
                .andExpect(jsonPath("$.content[1].id").value(newer.getId()));

        mockMvc.perform(get("/api/v1/mail-receipts").param("sort", "receivedAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.content[1].id").value(older.getId()));
    }

    @Test
    void 목록_정렬은_지원하지_않는_필드와_방향을_막는다() throws Exception {
        mockMvc.perform(get("/api/v1/mail-receipts").param("sort", "unknown,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.fieldErrors.sort").exists());

        mockMvc.perform(get("/api/v1/mail-receipts").param("sort", "receivedAt,sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.fieldErrors.sort").exists());
    }

    @Test
    void 수동_매칭은_supplierId가_없으면_400으로_막는다() throws Exception {
        mockMvc.perform(patch("/api/v1/mail-receipts/{receiptId}/supplier", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.fieldErrors.supplierId").exists());
    }

    private MailReceipt saveReceipt(String messageId, OffsetDateTime receivedAt) {
        return mailReceiptRepository.save(MailReceipt.builder()
                .messageId(messageId)
                .senderEmail("sender@example.com")
                .subject("CBAM 자료")
                .body("본문")
                .status(MailReceiptStatus.UNMATCHED)
                .receivedAt(receivedAt)
                .build());
    }
}
