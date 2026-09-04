package com.skala.cbam.ai.dto;

import java.util.List;

/**
 * 22~25번 추출에 넣는 것. {@code docs/product/prompts/01-extraction.md} 의 「사용자 메시지」 그대로다.
 *
 * <p><b>{@link #registeredParts} 를 매번 넣는다.</b> 안 넣으면 모델이 그럴듯한 부품명을 지어내고,
 * 그러면 25번의 「미등록 부품」이 한 번도 나오지 않는다 — 담당자는 매칭이 잘 됐다고 믿게 된다.
 * 실제로 확인했다: 목록을 준 뒤에야 등록되지 않은 품명이 {@code unregisteredParts} 로 잡혔다.
 *
 * @param mailBody 메일 본문. 첨부를 못 읽어도 본문만으로 분석할 수 있다
 * @param attachments 첨부에서 뽑아낸 텍스트. 파일 → 텍스트는 LLM 이 아니라 라이브러리의 몫이다
 */
public record ExtractionInput(
        String supplierName,
        String receivedAt,
        String reportingMonth,
        String mailBody,
        List<AttachmentText> attachments,
        List<RegisteredPart> registeredParts
) {

    /** 첨부 하나에서 뽑아낸 글자. {@code attachmentId} 는 항목의 {@code source} 로 되돌아온다. */
    public record AttachmentText(Long attachmentId, String fileName, String text) {
    }

    public record RegisteredPart(Long id, String name, String cnCode, String supplierName) {
    }

    /** 본문도 첨부 텍스트도 없으면 모델을 부를 이유가 없다 — 부르지 않고 NO_ATTACHMENT 로 끝낸다. */
    public boolean hasNothingToRead() {
        boolean emptyBody = mailBody == null || mailBody.isBlank();
        boolean emptyAttachments = attachments == null || attachments.stream()
                .allMatch(a -> a == null || a.text() == null || a.text().isBlank());
        return emptyBody && emptyAttachments;
    }
}
