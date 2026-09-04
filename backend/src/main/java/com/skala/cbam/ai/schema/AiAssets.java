package com.skala.cbam.ai.schema;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 응답 스키마와 시스템 프롬프트를 classpath 에서 읽는다 (ADR-0012 ⑤).
 *
 * <p><b>원본은 {@code docs/product/prompts/} 한 벌뿐이다.</b> Gradle 의 {@code processResources} 가
 * 그것을 {@code classpath:ai/} 로 복사한다 — 두 벌을 두면 갈라지고, 갈라지면
 * {@code npm run ai:verify} 가 검사한 것과 서버가 실제로 보내는 것이 달라진다.
 *
 * <p>스키마에서 {@code $schema}·{@code $id} 를 벗겨서 준다. 구조화 출력은 그 메타 키워드를 모른다 —
 * <b>실제로 호출해서 확인했다.</b> 벗기지 않으면 400 이 난다.
 */
@Component
public class AiAssets {

    public static final String EXTRACTION_SCHEMA_NAME = "cbam_extraction";
    public static final String DRAFT_SCHEMA_NAME = "cbam_feedback_draft";

    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    public AiAssets(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 22~25번 추출 스키마. */
    public JsonNode extractionSchema() {
        return schema("extraction");
    }

    /** 42~45번 초안 스키마. */
    public JsonNode draftSchema() {
        return schema("draft");
    }

    public String extractionSystemPrompt() {
        return systemPrompt("extraction");
    }

    public String draftSystemPrompt() {
        return systemPrompt("draft");
    }

    private JsonNode schema(String name) {
        return schemaCache.computeIfAbsent(name, key -> {
            // Jackson 3 의 readTree 는 검사 예외를 던지지 않는다
            return stripMetaKeywords(read("ai/" + key + ".schema.json", objectMapper::readTree));
        });
    }

    private String systemPrompt(String name) {
        return promptCache.computeIfAbsent(name, key -> read("ai/" + key + ".system.txt", stream -> {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    private <T> T read(String path, java.util.function.Function<InputStream, T> parser) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "AI 원본을 classpath 에서 찾을 수 없다: " + path
                            + " — docs/product/prompts/ 에서 복사되었는지 확인한다 (build.gradle 의 checkAiSchemas)");
        }
        try (InputStream stream = resource.getInputStream()) {
            return parser.apply(stream);
        } catch (IOException e) {
            throw new UncheckedIOException("AI 원본을 읽지 못했다: " + path, e);
        }
    }

    /**
     * {@code $schema}·{@code $id} 를 재귀로 걷어낸다.
     *
     * <p>둘은 JSON Schema 의 메타 키워드라 파일에는 있어야 하지만(에디터·검증기가 읽는다)
     * 구조화 출력은 모르는 키워드로 보고 400 을 낸다.
     */
    private JsonNode stripMetaKeywords(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            node.properties().forEach(entry -> {
                if (!"$schema".equals(entry.getKey()) && !"$id".equals(entry.getKey())) {
                    copy.set(entry.getKey(), stripMetaKeywords(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            var copy = objectMapper.createArrayNode();
            node.forEach(child -> copy.add(stripMetaKeywords(child)));
            return copy;
        }
        return node;
    }
}
