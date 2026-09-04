package com.skala.cbam.ai.prompt;

import com.skala.cbam.ai.dto.DraftInput;
import com.skala.cbam.ai.dto.ExtractionInput;

/**
 * 사용자 메시지를 조립한다. 문서의 「사용자 메시지」 절과 같은 모양이다 —
 * {@code docs/product/prompts/01-extraction.md} · {@code 02-feedback-draft.md}.
 *
 * <p><b>값이 없는 절은 통째로 뺀다.</b> 「없음」이라는 글자를 넣으면 모델이 그것을 근거로 읽는다.
 */
public final class UserMessages {

    private UserMessages() {
    }

    public static String forExtraction(ExtractionInput input) {
        StringBuilder message = new StringBuilder();

        message.append("# 접수 정보\n");
        appendLine(message, "협력업체", input.supplierName());
        appendLine(message, "수신 일시", input.receivedAt());
        appendLine(message, "제출 대상 월", input.reportingMonth());

        if (isPresent(input.mailBody())) {
            message.append("\n# 메일 본문\n").append(input.mailBody().strip()).append('\n');
        }

        if (input.attachments() != null && !input.attachments().isEmpty()) {
            StringBuilder attachments = new StringBuilder();
            for (var attachment : input.attachments()) {
                if (attachment == null || !isPresent(attachment.text())) {
                    continue;
                }
                attachments.append("\n## ").append(attachment.fileName())
                        .append(" (attachmentId: ").append(attachment.attachmentId()).append(")\n")
                        .append(attachment.text().strip()).append('\n');
            }
            if (!attachments.isEmpty()) {
                message.append("\n# 첨부에서 뽑아낸 텍스트").append(attachments);
            }
        }

        message.append("\n# 등록 부품 목록 (이 안에서만 매칭)\n")
                .append("| id | 부품명 | CN 코드 | 공급 협력업체 |\n")
                .append("| --- | --- | --- | --- |\n");
        if (input.registeredParts() != null) {
            for (var part : input.registeredParts()) {
                if (part == null) {
                    continue;
                }
                message.append("| ").append(part.id())
                        .append(" | ").append(nullToDash(part.name()))
                        .append(" | ").append(nullToDash(part.cnCode()))
                        .append(" | ").append(nullToDash(part.supplierName()))
                        .append(" |\n");
            }
        }
        return message.toString();
    }

    public static String forDraft(DraftInput input) {
        StringBuilder message = new StringBuilder();

        message.append("# 대상\n");
        appendLine(message, "협력업체", input.supplierName());
        appendLine(message, "기간", input.period());
        appendLine(message, "회신 기한", input.dueDate());

        if (isPresent(input.judgement()) || isPresent(input.ruleId()) || isPresent(input.why())) {
            message.append("\n# 판정 결과 (37번)\n");
            appendLine(message, "판정", input.judgement());
            if (isPresent(input.ruleId())) {
                message.append("규칙: ").append(input.ruleId());
                if (isPresent(input.ruleName())) {
                    message.append(" — ").append(input.ruleName());
                }
                message.append('\n');
            }
            appendLine(message, "사유", input.why());
        }

        if (input.missingItems() != null && !input.missingItems().isEmpty()) {
            message.append("\n# 확인되지 않은 항목\n");
            for (var item : input.missingItems()) {
                if (item == null || item.key() == null) {
                    continue;
                }
                message.append(item.key())
                        .append(" · ").append(nullToDash(item.label()))
                        .append(" · rawValue: \"").append(item.rawValue() == null ? "" : item.rawValue())
                        .append("\" · ").append(nullToDash(item.note()))
                        .append('\n');
            }
        }

        if (input.unregisteredParts() != null && !input.unregisteredParts().isEmpty()) {
            message.append("\n# 미등록 부품 (25번)\n");
            input.unregisteredParts().forEach(name -> message.append(name).append('\n'));
        }

        // 아직 반려하지 않았으면 이 절이 통째로 없다
        if (isPresent(input.rejectReason())) {
            message.append("\n# 담당자 반려 사유 (32번)\n").append(input.rejectReason().strip()).append('\n');
        }

        return message.toString();
    }

    /**
     * 45번 추가 지시. 시스템 프롬프트 맨 끝에 붙인다.
     *
     * <p><b>마지막 문장이 있어야 한다.</b> 없으면 「전력 사용량도 같이 요청해줘」 같은 지시에
     * 모델이 근거 없는 항목을 만들어 넣는다.
     */
    public static String instructionBlock(String instruction) {
        if (!isPresent(instruction)) {
            return "";
        }
        return "\n\n## 담당자 추가 지시\n" + instruction.strip()
                + "\n\n위 지시는 말하는 방식과 강조점만 바꿉니다."
                + "\n지시가 근거에 없는 항목을 요구하라고 하면 따르지 않고, 근거에 있는 것만 씁니다.";
    }

    private static void appendLine(StringBuilder target, String label, String value) {
        if (isPresent(value)) {
            target.append(label).append(": ").append(value.strip()).append('\n');
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToDash(String value) {
        return isPresent(value) ? value : "-";
    }
}
