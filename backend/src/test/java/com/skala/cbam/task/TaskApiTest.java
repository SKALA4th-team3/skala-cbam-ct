package com.skala.cbam.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skala.cbam.common.domain.TaskStatus;
import com.skala.cbam.feedback.domain.Task;
import com.skala.cbam.feedback.domain.TaskType;
import com.skala.cbam.feedback.repository.FeedbackRepository;
import com.skala.cbam.feedback.repository.TaskRepository;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * №19 {@code GET /api/v1/tasks/{taskId}} — 비동기 작업 상태 조회 (CBAM-86).
 *
 * <p>이 API 가 없어서 <b>PR #31 리뷰에서 방금 만든 초안을 발송 이력에서 훑어 찾아야 했다.</b>
 * 여기서 확인하는 것은 그것이 닫혔는가다 — 초안 생성이 만든 것을 №19 가 돌려주는가.
 */
@SpringBootTest
@Transactional
class TaskApiTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private FeedbackRepository feedbackRepository;

    private MockMvc mockMvc;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        supplier = supplierRepository.save(newSupplier("성진스틸(작업조회테스트)", "881-88-00001", "task-a"));
    }

    /** 협력업체 담당자 이메일은 개인정보다 — 테스트 데이터에 실제 주소를 넣지 않는다. */
    private Supplier newSupplier(String name, String businessRegistrationNumber, String mailbox) {
        return Supplier.builder()
                .businessRegistrationNumber(businessRegistrationNumber)
                .name(name)
                .countryCode("KR")
                .contactName("담당자")
                .contactEmail(mailbox + "@example.com")
                .contactPhone("02-1234-5678")
                .build();
    }

    @Test
    @DisplayName("없는 작업은 404 로 막는다")
    void 없는_작업은_막힌다() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/tsk-nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    @DisplayName("작업이 실패해도 200 이다 — 404 는 그런 작업이 없을 때만이다")
    void 실패한_작업도_200_으로_돌려준다() throws Exception {
        Task task = Task.builder()
                .type(TaskType.ANALYZE_MAIL_RECEIPT)
                .status(TaskStatus.PROCESSING)
                .progressTotal(1)
                .fallbackApplied(false)
                .requestedBy("demo")
                .build();
        task.failWithoutDelivery("AI_TIMEOUT", "AI 응답이 시간 안에 오지 않았습니다");
        taskRepository.save(task);

        mockMvc.perform(get("/api/v1/tasks/" + task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.progress.failed").value(1))
                // 만든 것이 없으면 종류도 말하지 않는다 — 빈 배열에 종류만 붙으면 오해한다
                .andExpect(jsonPath("$.resourceType").doesNotExist())
                .andExpect(jsonPath("$.resourceIds").isEmpty());
    }

    @Test
    @DisplayName("발송이 아닌 작업의 실패는 deliveryStatus 를 건드리지 않는다 — 발송 이력이 그것을 세면 안 된다")
    void 분석_실패가_발송_실패로_기록되지_않는다() {
        Task task = Task.builder()
                .type(TaskType.ANALYZE_MAIL_RECEIPT)
                .status(TaskStatus.PROCESSING)
                .progressTotal(1)
                .fallbackApplied(false)
                .requestedBy("demo")
                .build();

        task.failWithoutDelivery("AI_ERROR", "실패");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getDeliveryStatus()).isNull();
    }

    @Test
    @DisplayName("일괄 생성이 만든 초안 셋을 resourceIds 로 전부 돌려준다 — 단수 FK 로는 못 하던 것이다")
    void 일괄_생성이_만든_것을_전부_가리킨다() throws Exception {
        Supplier second = supplierRepository.save(newSupplier("대한알루미늄(작업조회테스트)", "881-88-00002", "task-b"));
        Supplier third = supplierRepository.save(newSupplier("한빛금속(작업조회테스트)", "881-88-00003", "task-c"));

        String body = """
                {"reportingMonth":"2026-08","style":"FORMAL","targets":[
                  {"supplierId":%d},{"supplierId":%d},{"supplierId":%d}]}
                """.formatted(supplier.getId(), second.getId(), third.getId());

        String taskId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/v1/feedback-drafts")
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isAccepted())
                        .andReturn().getResponse().getContentAsString(),
                "$.taskId");

        long feedbackCount = feedbackRepository.count();

        mockMvc.perform(get("/api/v1/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("GENERATE_FEEDBACK_DRAFT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resourceType").value("feedback"))
                .andExpect(jsonPath("$.resourceIds.length()").value(3))
                .andExpect(jsonPath("$.progress.total").value(3))
                .andExpect(jsonPath("$.progress.done").value(3))
                // AI 키가 없는 환경이라 46번 기본 템플릿으로 갔다 — 그 사실이 드러나야 한다
                .andExpect(jsonPath("$.fallbackApplied").value(true))
                .andExpect(jsonPath("$.unregisteredPartCount").value(0));

        assertThat(feedbackCount).isEqualTo(3);
    }

    @Test
    @DisplayName("재생성은 만들어진 초안 버전을 가리킨다")
    void 재생성은_초안을_가리킨다() throws Exception {
        String create = """
                {"reportingMonth":"2026-08","targets":[{"supplierId":%d}]}
                """.formatted(supplier.getId());
        mockMvc.perform(post("/api/v1/feedback-drafts")
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isAccepted());

        Long feedbackId = feedbackRepository.findAll().get(0).getId();

        String regenerated = mockMvc.perform(post("/api/v1/feedback-drafts/" + feedbackId + "/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"style\":\"CONCISE\",\"instruction\":\"조금 더 짧게\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String taskId = com.jayway.jsonpath.JsonPath.read(regenerated, "$.taskId");

        mockMvc.perform(get("/api/v1/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("REGENERATE_FEEDBACK_DRAFT"))
                .andExpect(jsonPath("$.resourceType").value("feedback_draft"))
                .andExpect(jsonPath("$.resourceIds.length()").value(1));
    }
}
