package com.skala.cbam.mail.service;

import com.skala.cbam.ai.dto.ExtractionInput;
import com.skala.cbam.mail.domain.Attachment;
import com.skala.cbam.mail.domain.AttachmentProcessStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 요구사항 22번 <b>①단계</b> — 파일에서 글자를 꺼낸다.
 *
 * <p><b>여기에 LLM 을 쓰지 않는다</b> ({@code docs/product/prompts/01-extraction.md}).
 * 암호 걸린 xlsx 는 모델에 넣어도 안 열리고, 표 구조는 라이브러리가 더 정확하게 읽는다.
 * ②단계(텍스트 → 배출 항목)만 모델의 몫이다.
 *
 * <p><b>지금 읽을 수 있는 것은 텍스트 계열뿐이다.</b> xlsx·pdf 를 읽으려면 Apache POI·PDFBox 를
 * 넣어야 하고, 그건 라이브러리 도입이라 ADR 이 먼저다({@code AI_WORKFLOW.md} 4항).
 * 못 읽은 첨부는 {@link AttachmentProcessStatus#UNSUPPORTED} 로 표시하고 <b>지어내지 않는다</b> —
 * 20번 「접수 불가」·22번 「분석 실패」와 같은 규칙이다.
 */
@Component
public class AttachmentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(AttachmentTextExtractor.class);

    /** 프롬프트에 통째로 실어 보낼 수 있는 크기. 넘으면 앞부분만 쓰고 그 사실을 남긴다. */
    private static final int MAX_CHARS = 40_000;

    private static final List<String> TEXT_SUFFIXES = List.of(".txt", ".csv", ".tsv", ".md", ".json");

    public Result extract(List<Attachment> attachments) {
        List<ExtractionInput.AttachmentText> texts = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();

        for (Attachment attachment : attachments == null ? List.<Attachment>of() : attachments) {
            if (attachment == null) {
                continue;
            }
            if (!isTextLike(attachment)) {
                attachment.markProcessStatus(AttachmentProcessStatus.UNSUPPORTED);
                unreadable.add(attachment.getOriginalFilename());
                continue;
            }
            try {
                String text = read(Path.of(attachment.getStorageUri()));
                if (text.isBlank()) {
                    attachment.markProcessStatus(AttachmentProcessStatus.FAILED);
                    unreadable.add(attachment.getOriginalFilename());
                    continue;
                }
                attachment.markProcessStatus(AttachmentProcessStatus.EXTRACTED);
                texts.add(new ExtractionInput.AttachmentText(
                        attachment.getId(), attachment.getOriginalFilename(), text));
            } catch (IOException e) {
                // DB 행은 있는데 원본 파일이 없거나 못 읽는다 — 없는 내용을 만들지 않는다
                log.warn("첨부를 읽지 못했다: id={}, uri={}", attachment.getId(), attachment.getStorageUri(), e);
                attachment.markProcessStatus(AttachmentProcessStatus.FAILED);
                unreadable.add(attachment.getOriginalFilename());
            }
        }
        return new Result(List.copyOf(texts), List.copyOf(unreadable));
    }

    private boolean isTextLike(Attachment attachment) {
        String mimeType = attachment.getMimeType() == null
                ? "" : attachment.getMimeType().toLowerCase(Locale.ROOT);
        if (mimeType.startsWith("text/") || mimeType.contains("csv") || mimeType.contains("json")) {
            return true;
        }
        String filename = attachment.getOriginalFilename() == null
                ? "" : attachment.getOriginalFilename().toLowerCase(Locale.ROOT);
        return TEXT_SUFFIXES.stream().anyMatch(filename::endsWith);
    }

    private String read(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        // 잘랐다는 사실을 모델에게도 알린다 — 뒤가 없는 것을 「자료에 없다」로 읽으면 안 된다
        return text.substring(0, MAX_CHARS) + "\n…(이 첨부는 길이 제한으로 여기까지만 읽었습니다)";
    }

    /**
     * @param texts 모델에 넣을 첨부 텍스트
     * @param unreadableFileNames 읽지 못한 첨부의 파일명. 하나도 못 읽었으면 №16 의 실패 사유가 된다
     */
    public record Result(List<ExtractionInput.AttachmentText> texts, List<String> unreadableFileNames) {

        public boolean readNothing() {
            return texts.isEmpty();
        }

        public boolean hadAttachments() {
            return !texts.isEmpty() || !unreadableFileNames.isEmpty();
        }
    }
}
