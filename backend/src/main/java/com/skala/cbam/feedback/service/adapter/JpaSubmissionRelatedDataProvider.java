package com.skala.cbam.feedback.service.adapter;

import com.skala.cbam.feedback.service.port.SubmissionRelatedDataProvider;
import com.skala.cbam.submission.domain.ExtractionField;
import com.skala.cbam.submission.domain.Judgement;
import com.skala.cbam.submission.domain.Submission;
import com.skala.cbam.submission.domain.SubmissionStatus;
import com.skala.cbam.submission.domain.UnregisteredPart;
import com.skala.cbam.submission.domain.UnregisteredPartStatus;
import com.skala.cbam.submission.repository.ExtractionFieldRepository;
import com.skala.cbam.submission.repository.SubmissionRepository;
import com.skala.cbam.submission.repository.SubmissionSpecifications;
import com.skala.cbam.submission.repository.UnregisteredPartRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 42~46번 초안의 <b>근거</b>를 제출 도메인에서 읽어 온다 (CBAM-99 ← CBAM-90).
 *
 * <p>여기서 주는 것만 안내문이 협력사에 요구할 수 있다 — 근거 밖 항목을 요구하면 서버가
 * 그 초안을 버리고 기본 템플릿으로 되돌린다(46번). 그래서 <b>없는 것을 채워 주지 않는다.</b>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaSubmissionRelatedDataProvider implements SubmissionRelatedDataProvider {

    /** 33번 필수 항목. 행이 없으면 「제출 자료에 기재가 없습니다」로 요구한다. */
    private static final Map<String, String> REQUIRED_FIELDS = new LinkedHashMap<>() {{
        put("partName", "부품명");
        put("production", "생산량");
    }};

    private final SubmissionRepository submissionRepository;
    private final ExtractionFieldRepository extractionFieldRepository;
    private final UnregisteredPartRepository unregisteredPartRepository;

    @Override
    public Optional<SubmissionInfo> findSubmissionInfo(Long submissionId) {
        return submissionRepository.findByIdWithSupplier(submissionId).map(this::toInfo);
    }

    @Override
    public List<SubmissionInfo> findDraftableSubmissions(YearMonth reportingMonth) {
        // 43번 일괄 — 이번 달 부적격 건. 미제출(NOT_SUBMITTED)은 제출 행이 없어 여기 안 잡힌다
        return submissionRepository.findAll(SubmissionSpecifications.matches(
                        null, reportingMonth.atDay(1), null, Judgement.UNQUALIFIED, null, null, null))
                .stream()
                .filter(s -> s.getStatus() != SubmissionStatus.CONFIRMED)
                .map(this::toInfo)
                .toList();
    }

    private SubmissionInfo toInfo(Submission submission) {
        List<ExtractionField> fields = extractionFieldRepository
                .findBySubmissionIdOrderByFieldCodeAscSequenceNumberAsc(submission.getId());
        List<String> unregistered = unregisteredPartRepository
                .findBySubmissionIdAndStatus(submission.getId(), UnregisteredPartStatus.OPEN)
                .stream().map(UnregisteredPart::getRawPartName).toList();

        return new SubmissionInfo(
                submission.getId(),
                submission.getSupplier().getId(),
                submission.getPartSupplierId(),
                submission.getReportingMonth() == null ? null
                        : YearMonth.from(submission.getReportingMonth()).toString(),
                submission.getJudgement() == Judgement.QUALIFIED,
                submission.getRejectionReasonCode(),
                submission.getRejectionReason(),
                submission.getJudgement() == null ? null : submission.getJudgement().name(),
                submission.getRejectionReasonCode(),
                null, // 규칙 이름 — 33~37번 규칙표가 코드와 이름을 함께 주면 채운다
                missingFieldsOf(fields),
                unregistered);
    }

    /**
     * 확인되지 않은 항목 둘을 한 목록으로 낸다. <b>둘을 구별해서 담는 것이 핵심이다</b> —
     * 안내문의 문장이 달라지고, 섞이면 협력사가 이미 보낸 값을 다시 보낸다.
     *
     * <ul>
     *   <li><b>R5 — 값은 있는데 못 옮겼다.</b> 행이 있고 {@code conversionFailureReason} 이 채워져 있다.
     *       {@code rawValue} 를 함께 넘겨 「값은 확인했으나 단위가 적혀 있지 않습니다」로 쓰게 한다</li>
     *   <li><b>R2 — 원문에 값이 없다.</b> <b>행 자체가 없다</b> — ERD 가 값 없는 행을 허용하지 않는다.
     *       필수 항목 중 행이 없는 것을 찾아 「제출 자료에 기재가 없습니다」로 쓰게 한다</li>
     * </ul>
     */
    private List<SubmissionInfo.MissingField> missingFieldsOf(List<ExtractionField> fields) {
        List<SubmissionInfo.MissingField> missing = new ArrayList<>();

        for (ExtractionField field : fields) {
            if (field.getConversionFailureReason() != null) {
                missing.add(new SubmissionInfo.MissingField(
                        field.getFieldCode(), field.getFieldCode(), field.getRawValue(),
                        "값은 확인했으나 표준 단위로 옮기지 못했습니다 (" + field.getConversionFailureReason() + ")"));
            }
        }

        List<String> present = fields.stream()
                .filter(f -> f.getConversionFailureReason() == null)
                .map(ExtractionField::getFieldCode)
                .toList();
        REQUIRED_FIELDS.forEach((code, label) -> {
            if (!present.contains(code)) {
                missing.add(new SubmissionInfo.MissingField(code, label, "", "제출 자료에 기재가 없습니다"));
            }
        });
        return missing;
    }
}
