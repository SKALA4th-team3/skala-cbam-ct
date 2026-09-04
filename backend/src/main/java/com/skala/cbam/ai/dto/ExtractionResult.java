package com.skala.cbam.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 22~25번 추출 결과. {@code docs/product/prompts/schema/extraction.schema.json} 과 같은 모양이다.
 *
 * <p>서버가 저장할 때는 {@link #items} 배열을 API 명세 v10 №21 의 {@code activityData} <b>객체</b>로
 * 옮긴다 — 구조화 출력 {@code strict} 가 동적 키(fuel_lng · fuel_anthracite …)를 표현할 수 없어
 * 배열로 받는다. 프런트의 {@code api/ai.js} 의 {@code activityDataFrom()} 과 같은 변환이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractionResult(
        String status,
        String failureReason,
        String sourceLanguage,
        List<Item> items,
        List<UnregisteredPart> unregisteredParts
) {

    public static final String ANALYZED = "ANALYZED";
    public static final String ANALYSIS_FAILED = "ANALYSIS_FAILED";
    /** №16 의 네 값 중, 담을 코드가 마땅치 않을 때 쓰는 것. 새 코드를 만들지 않는다. */
    public static final String PARSE_FAILED = "PARSE_FAILED";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String key,
            String label,
            String rawValue,
            /** 숫자·문자열·null 셋 다 온다 (№21 이 그렇다). 서버는 그대로 옮긴다. */
            Object value,
            String unit,
            String emissionScope,
            String conversionFailReason,
            String note,
            Double confidence,
            Source source
    ) {
        public Item withValueCleared(String note) {
            return new Item(key, label, rawValue, null, null, emissionScope,
                    conversionFailReason, note, confidence, source);
        }

        public Item withNote(String newNote) {
            return new Item(key, label, rawValue, value, unit, emissionScope,
                    conversionFailReason, newNote, confidence, source);
        }

        public Item withConfidence(double newConfidence) {
            return new Item(key, label, rawValue, value, unit, emissionScope,
                    conversionFailReason, note, newConfidence, source);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(Long attachmentId, String locator) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UnregisteredPart(String rawPartName, String note) {
    }

    /** 정리한 결과와 무엇을 고쳤는지. 고친 내역은 로그와 테스트에서 본다. */
    public record Normalized(ExtractionResult result, List<String> repairs) {
    }

    /**
     * <b>모델이 지키지 않는 불변식을 서버가 강제한다.</b>
     *
     * <p>이건 판단이 아니라 정합성 복구다 — 어느 쪽이 맞는지 사람이 고를 자리가 없다.
     * 실제 호출로 확인한 것들이라 「혹시 몰라서」 넣은 방어가 아니다.
     *
     * <ol>
     *   <li><b>{@code conversionFailReason} 이 있는데 {@code value} 가 남아 있다</b> —
     *       프롬프트로 두 번 요청해도 모델이 단위 없는 숫자를 value 에 남긴다.
     *       그대로 저장하면 <b>단위 없는 배출량</b>이 신고 수치가 된다. 비운다.</li>
     *   <li><b>{@code ANALYSIS_FAILED} 인데 배열에 값이 있다</b> — 입력으로 준 등록 부품 목록을
     *       결과로 옮겨 담는 경우를 봤다. 읽어낸 것이 없으면 배열도 비어야 한다.</li>
     *   <li><b>{@code note} 가 비었다</b> — 24번 「사유를 남긴다」가 지켜지지 않은 것이다.
     *       담당자가 볼 문장을 서버가 채운다.</li>
     * </ol>
     */
    public Normalized normalize() {
        List<String> repairs = new ArrayList<>();

        if (ANALYSIS_FAILED.equals(status)) {
            if (notEmpty(items) || notEmpty(unregisteredParts)) {
                repairs.add("분석 실패인데 items·unregisteredParts 가 비어 있지 않아 비웠다");
            }
            String reason = (failureReason == null || failureReason.isBlank()) ? PARSE_FAILED : failureReason;
            if (!reason.equals(failureReason)) {
                repairs.add("분석 실패인데 failureReason 이 없어 " + PARSE_FAILED + " 로 뒀다");
            }
            return new Normalized(
                    new ExtractionResult(ANALYSIS_FAILED, reason, sourceLanguage, List.of(), List.of()),
                    List.copyOf(repairs));
        }

        String cleanFailureReason = failureReason;
        if (failureReason != null) {
            repairs.add("분석에 성공했는데 failureReason 이 채워져 있어 비웠다: " + failureReason);
            cleanFailureReason = null;
        }

        List<Item> cleanItems = new ArrayList<>();
        for (Item item : items == null ? List.<Item>of() : items) {
            if (item == null || item.key() == null || item.key().isBlank()) {
                repairs.add("key 가 없는 항목을 버렸다");
                continue;
            }
            Item current = item;

            if (current.conversionFailReason() != null && current.value() != null) {
                repairs.add(current.key() + ": 변환 실패(" + current.conversionFailReason()
                        + ") 인데 value 가 남아 있어 비웠다 — 단위 없는 값을 저장하지 않는다");
                current = current.withValueCleared(current.note());
            }

            if (current.note() == null || current.note().isBlank()) {
                repairs.add(current.key() + ": note 가 비어 서버가 채웠다");
                current = current.withNote(defaultNote(current));
            }

            double confidence = current.confidence() == null ? 0.0 : current.confidence();
            if (confidence < 0.0 || confidence > 1.0) {
                repairs.add(current.key() + ": confidence 가 0~1 밖이라 잘랐다 (" + confidence + ")");
                current = current.withConfidence(Math.clamp(confidence, 0.0, 1.0));
            } else if (current.confidence() == null) {
                current = current.withConfidence(0.0);
            }

            cleanItems.add(current);
        }

        // 같은 부품이 여러 번 오면 담당자가 같은 것을 두 번 등록하게 된다
        Set<String> seen = new LinkedHashSet<>();
        List<UnregisteredPart> cleanParts = new ArrayList<>();
        for (UnregisteredPart part : unregisteredParts == null ? List.<UnregisteredPart>of() : unregisteredParts) {
            if (part == null || part.rawPartName() == null || part.rawPartName().isBlank()) {
                continue;
            }
            String name = part.rawPartName().strip();
            if (!seen.add(name)) {
                repairs.add("미등록 부품이 중복돼 하나만 남겼다: " + name);
                continue;
            }
            cleanParts.add(new UnregisteredPart(name, part.note()));
        }

        return new Normalized(
                new ExtractionResult(ANALYZED, cleanFailureReason, sourceLanguage,
                        List.copyOf(cleanItems), List.copyOf(cleanParts)),
                List.copyOf(repairs));
    }

    /** 24번 — 비운 이유를 반드시 남긴다. 모델이 안 남겼으면 서버가 사실만 적는다. */
    private static String defaultNote(Item item) {
        if (item.conversionFailReason() != null) {
            return "표준 단위로 옮기지 못했다 (" + item.conversionFailReason() + ")";
        }
        if (item.value() == null) {
            return "원문에서 값을 찾지 못했다";
        }
        return "원문에서 그대로 읽었다";
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    public boolean isFailed() {
        return ANALYSIS_FAILED.equals(status);
    }
}
