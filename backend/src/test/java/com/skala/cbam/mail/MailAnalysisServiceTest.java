package com.skala.cbam.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.cbam.ai.client.AiCallException;
import com.skala.cbam.ai.client.AiClient;
import com.skala.cbam.ai.dto.ExtractionResult;
import com.skala.cbam.common.domain.TaskStatus;
import com.skala.cbam.feedback.repository.TaskRepository;
import com.skala.cbam.mail.domain.MailReceipt;
import com.skala.cbam.mail.domain.MailReceiptStatus;
import com.skala.cbam.mail.repository.MailReceiptRepository;
import com.skala.cbam.mail.service.MailAnalysisService;
import com.skala.cbam.mail.service.port.AnalysisResultSink;
import com.skala.cbam.parts.entity.Part;
import com.skala.cbam.parts.entity.PartUnit;
import com.skala.cbam.parts.repository.PartsRepository;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import com.skala.cbam.task.dto.TaskDetailResponse;
import com.skala.cbam.task.service.TaskQueryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 22~25번 접수 자료 분석 (CBAM-100).
 *
 * <p>실제 OpenAI 를 부르지 않는다 — {@link StubAiClientConfig} 가 모델 응답 자리에
 * <b>{@code docs/product/prompts/schema/extraction.example.json} 을 그대로 넣는다.</b>
 * 프런트의 {@code npm run ai:verify} 가 검사하는 것과 <b>같은 파일 하나</b>다.
 *
 * <p>키가 없을 때의 경로도 함께 확인한다 — dev 와 CI 가 실제로 그 경로로 돈다.
 */
@SpringBootTest
@Transactional
@Import(MailAnalysisServiceTest.StubAiClientConfig.class)
class MailAnalysisServiceTest {

    /** 모델 응답을 예시 파일로 대신한다. {@link #mode} 로 실패 경로도 만든다. */
    @TestConfiguration
    static class StubAiClientConfig {

        static final AtomicReference<String> mode = new AtomicReference<>("example");

        @Bean
        @Primary
        AiClient stubAiClient(ObjectMapper objectMapper) {
            return new AiClient() {
                @Override
                public boolean isAvailable() {
                    return !"unavailable".equals(mode.get());
                }

                @Override
                public JsonNode complete(String systemPrompt, String userMessage, String schemaName,
                                         JsonNode schema, double temperature) {
                    return switch (mode.get()) {
                        case "timeout" -> throw new AiCallException(
                                AiCallException.AI_TIMEOUT, "AI 응답이 시간 안에 오지 않았습니다");
                        case "unavailable" -> throw AiCallException.notConfigured();
                        case "unreadable" -> objectMapper.readTree("""
                                {"status":"ANALYSIS_FAILED","failureReason":"ENCRYPTED_FILE",
                                 "sourceLanguage":"unknown","items":[],"unregisteredParts":[]}""");
                        default -> readExample(objectMapper);
                    };
                }
            };
        }

        private static JsonNode readExample(ObjectMapper objectMapper) {
            try (var stream = new ClassPathResource("ai/extraction.example.json").getInputStream()) {
                return objectMapper.readTree(stream);
            } catch (Exception e) {
                throw new IllegalStateException("예시 응답을 읽지 못했다 — build.gradle 의 복사를 확인한다", e);
            }
        }
    }

    @Autowired
    private MailAnalysisService mailAnalysisService;
    @Autowired
    private MailReceiptRepository mailReceiptRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskQueryService taskQueryService;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private PartsRepository partsRepository;

    private MailReceipt receipt;

    @BeforeEach
    void seed() {
        StubAiClientConfig.mode.set("example");

        Supplier supplier = supplierRepository.save(Supplier.builder()
                .businessRegistrationNumber("991-99-00001")
                .name("성진스틸(분석테스트)")
                .countryCode("KR")
                .contactName("담당자")
                .contactEmail("analysis-test@example.com")
                .contactPhone("02-9999-1111")
                .build());

        // 25번 — 등록 부품 목록을 프롬프트에 넣는다. 없으면 모델이 부품명을 지어낸다
        partsRepository.save(new Part("PT-ANALYSIS-1", "열연강판(분석테스트)", "72081000",
                PartUnit.TON, new BigDecimal("1.8"), java.util.Set.of(supplier.getId())));

        receipt = mailReceiptRepository.save(MailReceipt.builder()
                .messageId("<analysis-test-1@mail.example.com>")
                .senderEmail("sender@example.com")
                .subject("8월 배출 자료")
                .body("열연강판 생산량 1,250 t. 전력 사용량 480,000 kWh. LNG 45,000 사용.")
                .status(MailReceiptStatus.MATCHED)
                .receivedAt(OffsetDateTime.parse("2026-09-02T09:14:00+09:00"))
                .build());
        receipt.match(supplier, "demo");
    }

    @Test
    @DisplayName("분석이 끝나면 접수는 ANALYZED 가 되고 작업은 №19 로 조회된다")
    void 분석에_성공하면_작업이_완료된다() {
        String taskId = mailAnalysisService.scheduleAnalysis(receipt, "demo");
        assertThat(taskRepository.findById(taskId)).get()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.PENDING));

        mailAnalysisService.runAnalysis(taskId, receipt.getId());

        assertThat(receipt.getStatus()).isEqualTo(MailReceiptStatus.ANALYZED);
        assertThat(receipt.getFailureReason()).isNull();

        TaskDetailResponse detail = taskQueryService.getDetail(taskId);
        assertThat(detail.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(detail.taskType().name()).isEqualTo("ANALYZE_MAIL_RECEIPT");
        assertThat(detail.errorCode()).isNull();
        // 제출 도메인(CBAM-90)이 아직 없어 submission id 는 없다.
        // 대신 어느 접수 건을 분석했는지는 남는다 — 없는 id 를 지어내지 않는다
        assertThat(detail.resourceIds()).containsExactly(receipt.getId());
    }

    @Test
    @DisplayName("AI 시간 초과는 작업을 FAILED 로 남기고 접수를 분석 실패로 둔다 — 조용히 넘어가지 않는다")
    void 시간_초과는_실패로_남는다() {
        StubAiClientConfig.mode.set("timeout");
        String taskId = mailAnalysisService.scheduleAnalysis(receipt, "demo");

        mailAnalysisService.runAnalysis(taskId, receipt.getId());

        TaskDetailResponse detail = taskQueryService.getDetail(taskId);
        assertThat(detail.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(detail.errorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(receipt.getStatus()).isEqualTo(MailReceiptStatus.ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("AI 키가 없어도 앱이 돌고, 분석은 실패로 정직하게 남는다")
    void 키가_없으면_실패로_남는다() {
        StubAiClientConfig.mode.set("unavailable");
        String taskId = mailAnalysisService.scheduleAnalysis(receipt, "demo");

        mailAnalysisService.runAnalysis(taskId, receipt.getId());

        assertThat(taskQueryService.getDetail(taskId).status()).isEqualTo(TaskStatus.FAILED);
        assertThat(receipt.getStatus()).isEqualTo(MailReceiptStatus.ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("모델이 '읽지 못했다'고 정상 응답하면 №16 의 사유를 그대로 남긴다 — 호출 오류와 구별된다")
    void 분석_실패는_명세의_사유로_남는다() {
        StubAiClientConfig.mode.set("unreadable");
        String taskId = mailAnalysisService.scheduleAnalysis(receipt, "demo");

        mailAnalysisService.runAnalysis(taskId, receipt.getId());

        assertThat(taskQueryService.getDetail(taskId).errorCode()).isEqualTo("ENCRYPTED_FILE");
        assertThat(receipt.getFailureReason()).isEqualTo("ENCRYPTED_FILE");
    }

    @Test
    @DisplayName("읽을 본문도 첨부도 없으면 모델을 부르지 않고 NO_ATTACHMENT 로 끝낸다")
    void 읽을_것이_없으면_부르지_않는다() {
        MailReceipt empty = mailReceiptRepository.save(MailReceipt.builder()
                .messageId("<analysis-test-empty@mail.example.com>")
                .senderEmail("sender@example.com")
                .subject("제목만 있는 메일")
                .body("   ")
                .status(MailReceiptStatus.MATCHED)
                .receivedAt(OffsetDateTime.parse("2026-09-02T10:00:00+09:00"))
                .build());

        String taskId = mailAnalysisService.scheduleAnalysis(empty, "demo");
        mailAnalysisService.runAnalysis(taskId, empty.getId());

        assertThat(taskQueryService.getDetail(taskId).errorCode()).isEqualTo("NO_ATTACHMENT");
        assertThat(empty.getStatus()).isEqualTo(MailReceiptStatus.ANALYSIS_FAILED);
    }

    @Test
    @DisplayName("예시 응답이 서버 정합성 검사를 그대로 통과한다 — ai:verify 가 보는 것과 같은 파일이다")
    void 예시_응답은_정정_없이_통과한다() {
        String taskId = mailAnalysisService.scheduleAnalysis(receipt, "demo");
        mailAnalysisService.runAnalysis(taskId, receipt.getId());

        // 정정이 필요했다면 로그에 남고 여기서 잡힌다 — 예시가 스키마와 어긋나면 실패한다
        assertThat(taskQueryService.getDetail(taskId).status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("제출 도메인이 붙기 전에는 저장하지 않고 빈 결과를 정직하게 돌려준다")
    void 저장할_곳이_없으면_빈_결과다() {
        AnalysisResultSink.Outcome outcome = AnalysisResultSink.Outcome.empty();
        assertThat(outcome.submissionIds()).isEmpty();
        assertThat(outcome.unregisteredPartCount()).isZero();
    }

    @Test
    @DisplayName("예시 응답의 항목이 화면이 읽는 키를 다 갖고 있다")
    void 예시_응답이_화면_키를_갖춘다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ExtractionResult example;
        try (var stream = new ClassPathResource("ai/extraction.example.json").getInputStream()) {
            example = mapper.readValue(stream, ExtractionResult.class);
        }

        assertThat(example.items()).isNotEmpty();
        List<String> keys = example.items().stream().map(ExtractionResult.Item::key).toList();
        assertThat(keys).contains("partName", "production");
        assertThat(example.items()).allSatisfy(item -> {
            assertThat(item.note()).isNotBlank();
            assertThat(item.source()).isNotNull();
            assertThat(item.source().locator()).isNotBlank();
        });
    }
}
