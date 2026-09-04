package com.skala.cbam.feedback;

import com.skala.cbam.common.domain.DeliveryStatus;
import com.skala.cbam.common.domain.FeedbackStatus;
import com.skala.cbam.common.domain.TaskStatus;
import com.skala.cbam.feedback.domain.Feedback;
import com.skala.cbam.feedback.domain.FeedbackType;
import com.skala.cbam.feedback.dto.FeedbackConfirmRequest;
import com.skala.cbam.feedback.dto.FeedbackDraftCreateRequest;
import com.skala.cbam.feedback.dto.FeedbackDraftRegenerateRequest;
import com.skala.cbam.feedback.dto.FeedbackHistorySearchCondition;
import com.skala.cbam.feedback.dto.FeedbackSendRequest;
import com.skala.cbam.feedback.error.FeedbackErrorCode;
import com.skala.cbam.feedback.error.FeedbackException;
import com.skala.cbam.feedback.repository.FeedbackDraftRepository;
import com.skala.cbam.feedback.repository.FeedbackRepository;
import com.skala.cbam.feedback.repository.TaskRepository;
import com.skala.cbam.feedback.service.FeedbackService;
import com.skala.cbam.supplier.domain.Supplier;
import com.skala.cbam.supplier.repository.SupplierRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CBAM-88 (42~53번 중 47·49번 제외) 이 되는 경우·막는 경우를 다 지키는지 확인한다.
 *
 * <p>실제 SMTP 가 없어서 발송은 기본적으로 502 로 막힌다 — 이게 명세가 원래 요구하는 정확한 동작이다.
 * "발송 되는 경우"까지 검증하려고 {@link FakeMailSenderConfig} 로 JavaMailSender 를 무해한
 * 가짜로 바꿔서 테스트한다 (CBAM-90에서 배운 것 — 항상 실패 경로만 타면서 "된다"고 우기지 않는다).
 */
@SpringBootTest
@Transactional
@Import(FeedbackServiceTest.FakeMailSenderConfig.class)
class FeedbackServiceTest {

    @TestConfiguration
    static class FakeMailSenderConfig {
        @Bean
        @Primary
        JavaMailSender fakeMailSender() {
            return new JavaMailSender() {
                @Override
                public MimeMessage createMimeMessage() {
                    throw new UnsupportedOperationException("이 테스트는 SimpleMailMessage 경로만 쓴다");
                }

                @Override
                public MimeMessage createMimeMessage(java.io.InputStream contentStream) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void send(MimeMessage mimeMessage) throws MailException {
                    // 아무것도 안 함 — 성공한 척
                }

                @Override
                public void send(MimeMessage... mimeMessages) throws MailException {
                }

                @Override
                public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
                }

                @Override
                public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
                }

                @Override
                public void send(SimpleMailMessage simpleMessage) throws MailException {
                    // 아무것도 안 함 — 성공한 척
                }

                @Override
                public void send(SimpleMailMessage... simpleMessages) throws MailException {
                }
            };
        }
    }

    @Autowired
    private FeedbackService feedbackService;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private FeedbackDraftRepository feedbackDraftRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private SupplierRepository supplierRepository;

    private Supplier daehan;

    @BeforeEach
    void seed() {
        daehan = supplierRepository.save(Supplier.builder()
                .businessRegistrationNumber("777-77-00001")
                .name("대한금속(피드백테스트)")
                .countryCode("KR")
                .contactName("김철수")
                .contactEmail("feedback-test-daehan@example.com")
                .contactPhone("02-7777-1111")
                .build());
    }

    private Feedback saveFeedback() {
        return feedbackRepository.save(Feedback.builder()
                .supplier(daehan).reportingMonth(LocalDate.of(2026, 9, 1))
                .type(FeedbackType.FEEDBACK).createdBy("demo").build());
    }

    // ── 42·43번: 초안 생성 ────────────────────────────────────────

    @Test
    void 초안_생성은_targets로_지정한_미제출_대상에_대해_만들어진다() {
        var request = new FeedbackDraftCreateRequest(
                "2026-09", null,
                List.of(new FeedbackDraftCreateRequest.Target(daehan.getId(), 101L)),
                "FORMAL");

        var response = feedbackService.createDrafts(request, "demo");

        assertThat(response.targetCount()).isEqualTo(1);
        assertThat(response.taskId()).startsWith("tsk-");
        assertThat(feedbackRepository.findAll()).hasSize(1);
        assertThat(feedbackDraftRepository.findAll()).hasSize(1);
    }

    @Test
    void 초안_생성은_대상이_없으면_막힌다() {
        // submissionIds·targets 둘 다 없으면 43번(일괄) 경로 — submission 도메인 Port가 항상
        // 빈 목록을 주므로 지금은 항상 대상이 없다.
        var request = new FeedbackDraftCreateRequest("2026-09", null, null, "FORMAL");

        assertThatThrownBy(() -> feedbackService.createDrafts(request, "demo"))
                .isInstanceOf(FeedbackException.class)
                .satisfies(e -> assertThat(((FeedbackException) e).errorCode())
                        .isEqualTo(FeedbackErrorCode.NO_TARGET));
    }

    // ── 44·46번: 초안 조회 ───────────────────────────────────────

    @Test
    void 초안_조회는_최신_버전과_버전_목록을_함께_반환한다() {
        var created = feedbackService.createDrafts(new FeedbackDraftCreateRequest(
                "2026-09", null, List.of(new FeedbackDraftCreateRequest.Target(daehan.getId(), 101L)), "FORMAL"),
                "demo");
        Long draftId = feedbackRepository.findAll().get(0).getId();

        var detail = feedbackService.getDetail(draftId, null);

        assertThat(detail.version()).isEqualTo(1);
        assertThat(detail.subject()).contains("대한금속(피드백테스트)");
        assertThat(detail.versions()).hasSize(1);
        assertThat(created.targetCount()).isEqualTo(1);
    }

    @Test
    void 초안_조회는_없는_건이면_막힌다() {
        assertThatThrownBy(() -> feedbackService.getDetail(999_999L, null))
                .isInstanceOf(FeedbackException.class)
                .satisfies(e -> assertThat(((FeedbackException) e).errorCode())
                        .isEqualTo(FeedbackErrorCode.FEEDBACK_DRAFT_NOT_FOUND));
    }

    // ── 45번: 재생성 ─────────────────────────────────────────────

    @Test
    void 재생성은_이전_버전을_보관하고_새_버전을_만든다() {
        Feedback feedback = saveFeedback();
        feedbackDraftRepository.save(com.skala.cbam.feedback.domain.FeedbackDraft.builder()
                .feedback(feedback).versionNumber((short) 1)
                .sourceType(com.skala.cbam.feedback.domain.DraftSourceType.FALLBACK_TEMPLATE)
                .style(com.skala.cbam.feedback.domain.FeedbackStyle.FORMAL)
                .subject("s1").body("b1").fallbackApplied(true).build());

        var response = feedbackService.regenerate(
                feedback.getId(), new FeedbackDraftRegenerateRequest("기한을 명시해주세요", "CONCISE"), "demo");

        assertThat(response.nextVersion()).isEqualTo(2);
        List<com.skala.cbam.feedback.domain.FeedbackDraft> versions =
                feedbackDraftRepository.findByFeedbackIdOrderByVersionNumberDesc(feedback.getId());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo((short) 2);
        assertThat(versions.get(1).getVersionNumber()).isEqualTo((short) 1); // 이전 버전이 지워지지 않았다
    }

    @Test
    void 재생성은_확정된_건이면_막힌다() {
        Feedback feedback = saveFeedback();
        feedbackDraftRepository.save(com.skala.cbam.feedback.domain.FeedbackDraft.builder()
                .feedback(feedback).versionNumber((short) 1)
                .sourceType(com.skala.cbam.feedback.domain.DraftSourceType.FALLBACK_TEMPLATE)
                .style(com.skala.cbam.feedback.domain.FeedbackStyle.FORMAL)
                .subject("s1").body("b1").fallbackApplied(true).build());
        feedback.confirm(1L, "kim@daehan.co.kr", "demo");
        feedbackRepository.save(feedback);

        assertThatThrownBy(() -> feedbackService.regenerate(
                feedback.getId(), new FeedbackDraftRegenerateRequest(null, "FORMAL"), "demo"))
                .isInstanceOf(FeedbackException.class)
                .satisfies(e -> assertThat(((FeedbackException) e).errorCode())
                        .isEqualTo(FeedbackErrorCode.NOT_REGENERATABLE));
    }

    // ── 48번: 확정 ───────────────────────────────────────────────

    @Test
    void 확정은_DRAFT_상태에서_READY_TO_SEND로_바뀐다() {
        Feedback feedback = saveFeedback();
        var draft = feedbackDraftRepository.save(com.skala.cbam.feedback.domain.FeedbackDraft.builder()
                .feedback(feedback).versionNumber((short) 1)
                .sourceType(com.skala.cbam.feedback.domain.DraftSourceType.FALLBACK_TEMPLATE)
                .style(com.skala.cbam.feedback.domain.FeedbackStyle.FORMAL)
                .subject("s1").body("b1").fallbackApplied(true).build());

        var response = feedbackService.confirm(
                feedback.getId(), new FeedbackConfirmRequest("READY_TO_SEND"), "demo");

        assertThat(response.status()).isEqualTo(FeedbackStatus.READY_TO_SEND);
        assertThat(response.recipient()).isEqualTo("feedback-test-daehan@example.com");
        assertThat(draft.getId()).isNotNull();
    }

    @Test
    void 확정은_이미_확정된_건이면_막힌다() {
        Feedback feedback = saveFeedback();
        feedbackDraftRepository.save(com.skala.cbam.feedback.domain.FeedbackDraft.builder()
                .feedback(feedback).versionNumber((short) 1)
                .sourceType(com.skala.cbam.feedback.domain.DraftSourceType.FALLBACK_TEMPLATE)
                .style(com.skala.cbam.feedback.domain.FeedbackStyle.FORMAL)
                .subject("s1").body("b1").fallbackApplied(true).build());
        feedback.confirm(1L, "kim@daehan.co.kr", "demo");
        feedbackRepository.save(feedback);

        assertThatThrownBy(() -> feedbackService.confirm(
                feedback.getId(), new FeedbackConfirmRequest("READY_TO_SEND"), "demo"))
                .isInstanceOf(FeedbackException.class)
                .satisfies(e -> assertThat(((FeedbackException) e).errorCode())
                        .isEqualTo(FeedbackErrorCode.ALREADY_CONFIRMED));
    }

    // ── 50·52번: 발송·재발송 ─────────────────────────────────────

    private Feedback confirmedFeedback() {
        Feedback feedback = saveFeedback();
        var draft = feedbackDraftRepository.save(com.skala.cbam.feedback.domain.FeedbackDraft.builder()
                .feedback(feedback).versionNumber((short) 1)
                .sourceType(com.skala.cbam.feedback.domain.DraftSourceType.FALLBACK_TEMPLATE)
                .style(com.skala.cbam.feedback.domain.FeedbackStyle.FORMAL)
                .subject("s1").body("b1").fallbackApplied(true).build());
        feedback.confirm(draft.getId(), "feedback-test-daehan@example.com", "demo");
        return feedbackRepository.save(feedback);
    }

    @Test
    void 발송은_확정된_건을_가짜_메일서버로_성공시킨다() {
        Feedback feedback = confirmedFeedback();

        var response = feedbackService.send(feedback.getId(), new FeedbackSendRequest(null), "demo");

        assertThat(response.attempt()).isEqualTo(1);
        assertThat(response.recipient()).isEqualTo("feedback-test-daehan@example.com");
        // 발송 성공해도 Feedback.status 는 READY_TO_SEND 에 머문다 — "보냈는지"는 Task.deliveryStatus로 판단한다
        Feedback reloaded = feedbackRepository.findById(feedback.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FeedbackStatus.READY_TO_SEND);
        assertThat(taskRepository.findById(response.taskId())).isPresent()
                .get().satisfies(t -> {
                    assertThat(t.getStatus()).isEqualTo(TaskStatus.COMPLETED);
                    assertThat(t.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
                });
    }

    @Test
    void 발송은_확정되지_않은_건이면_막힌다() {
        Feedback feedback = saveFeedback(); // 여전히 DRAFT

        assertThatThrownBy(() -> feedbackService.send(feedback.getId(), new FeedbackSendRequest(null), "demo"))
                .isInstanceOf(FeedbackException.class)
                .satisfies(e -> assertThat(((FeedbackException) e).errorCode())
                        .isEqualTo(FeedbackErrorCode.NOT_CONFIRMED));
    }

    @Test
    void 재발송은_reason_없으면_막힌다() {
        Feedback feedback = confirmedFeedback();
        feedbackService.send(feedback.getId(), new FeedbackSendRequest(null), "demo"); // 1차 발송 성공

        assertThatThrownBy(() -> feedbackService.send(feedback.getId(), new FeedbackSendRequest(null), "demo"))
                .isInstanceOf(FeedbackException.class)
                .satisfies(e -> assertThat(((FeedbackException) e).errorCode())
                        .isEqualTo(FeedbackErrorCode.RESEND_REASON_REQUIRED));
    }

    @Test
    void 재발송은_SEND_FAILED_사유면_바로_된다() {
        Feedback feedback = confirmedFeedback();
        feedbackService.send(feedback.getId(), new FeedbackSendRequest(null), "demo");

        var response = feedbackService.send(feedback.getId(), new FeedbackSendRequest("SEND_FAILED"), "demo");

        assertThat(response.attempt()).isEqualTo(2);
    }

    // ── 51·53번: 발송 이력 조회 ───────────────────────────────────

    @Test
    void 발송이력_조회는_deliveries_배열에_시도_기록을_담는다() {
        Feedback feedback = confirmedFeedback();
        feedbackService.send(feedback.getId(), new FeedbackSendRequest(null), "demo");

        var page = feedbackService.listHistories(
                new FeedbackHistorySearchCondition(daehan.getId(), null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        var item = page.content().get(0);
        assertThat(item.status()).isEqualTo(DeliveryStatus.SENT); // 발송 결과 — 검토 상태(FeedbackStatus)가 아니다
        assertThat(item.deliveries()).hasSize(1);
        assertThat(item.deliveries().get(0).attempt()).isEqualTo(1);
        assertThat(item.deliveries().get(0).status()).isEqualTo(DeliveryStatus.SENT);
    }
}
