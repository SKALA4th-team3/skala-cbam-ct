package com.skala.cbam.ai.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.skala.cbam.ai.config.AiProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 호환 {@code /chat/completions} 호출 (ADR-0012 ①).
 *
 * <p><b>의존성을 더하지 않았다.</b> Spring Boot 4 의 {@code spring-web} 에 있는 {@link RestClient}
 * 를 쓴다 — SDK 를 넣으면 제공자가 바뀔 때 그 SDK 가 코드 전체에 박힌다.
 *
 * <p><b>스트리밍으로 받는다</b>(ADR-0012 ②). 화면 계약은 202 + 폴링이라 실시간 타자 효과에 쓰는
 * 것이 아니다 — 목적은 <b>읽기 타임아웃</b>이다. 응답을 한 덩어리로 기다리면 긴 안내문에서
 * 60초 읽기 제한에 걸리는데, 스트리밍이면 토큰이 오는 동안 제한이 갱신된다.
 * <b>서버는 조각을 다 모아 완성된 JSON 만 돌려준다</b> — 반쯤 만들어진 초안이 저장되는 길을 만들지 않는다.
 */
@Component
public class OpenAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiClient(AiProperties properties, ObjectMapper objectMapper,
                        @Qualifier("aiRestClientBuilder") RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    @Override
    public JsonNode complete(String systemPrompt, String userMessage, String schemaName,
                             JsonNode schema, double temperature) {
        if (!properties.isConfigured()) {
            throw AiCallException.notConfigured();
        }

        ObjectNode body = buildRequestBody(systemPrompt, userMessage, schemaName, schema, temperature);

        try {
            return restClient.post()
                    .uri(properties.chatCompletionsUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((request, response) -> readStream(response), false);
        } catch (AiCallException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // 연결·읽기 타임아웃은 ResourceAccessException 안에 SocketTimeoutException 으로 들어온다
            if (hasCause(e, SocketTimeoutException.class)) {
                throw new AiCallException(AiCallException.AI_TIMEOUT, "AI 응답이 시간 안에 오지 않았습니다", e);
            }
            throw new AiCallException(AiCallException.AI_ERROR, "AI 서비스에 연결할 수 없습니다", e);
        } catch (RuntimeException e) {
            throw new AiCallException(AiCallException.AI_ERROR, "AI 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequestBody(String systemPrompt, String userMessage, String schemaName,
                                        JsonNode schema, double temperature) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.model());
        root.put("temperature", temperature);
        root.put("stream", true);

        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);

        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);
        root.putObject("response_format").put("type", "json_schema").set("json_schema", jsonSchema);

        return root;
    }

    /**
     * SSE 스트림을 모아 완성된 JSON 으로 만든다.
     *
     * <p>조각은 {@code data: {...}} 줄로 오고 {@code data: [DONE]} 으로 끝난다.
     * {@code delta.content} 를 이어 붙인 것이 응답 JSON 이다.
     */
    private JsonNode readStream(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().isError()) {
            String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            log.warn("AI 호출 실패: status={}, body={}", response.getStatusCode(), truncate(errorBody));
            throw new AiCallException(AiCallException.AI_ERROR,
                    "AI 서비스가 " + response.getStatusCode().value() + " 를 반환했습니다");
        }

        StringBuilder content = new StringBuilder();
        StringBuilder refusal = new StringBuilder();
        String finishReason = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(SSE_DATA_PREFIX)) {
                    continue;
                }
                String payload = line.substring(SSE_DATA_PREFIX.length()).trim();
                if (payload.isEmpty() || SSE_DONE.equals(payload)) {
                    continue;
                }
                JsonNode chunk = objectMapper.readTree(payload);
                JsonNode choice = chunk.path("choices").path(0);
                append(content, choice.path("delta").path("content"));
                append(refusal, choice.path("delta").path("refusal"));
                if (choice.hasNonNull("finish_reason")) {
                    finishReason = choice.get("finish_reason").asText();
                }
            }
        }

        // 46번 두 번째 실패 — 스키마를 지키기를 거부했다
        if (!refusal.isEmpty()) {
            throw AiCallException.refused(refusal.toString());
        }
        // 토큰 한도에 걸려 끊긴 JSON 을 파싱하려 들지 않는다
        if ("length".equals(finishReason)) {
            throw new AiCallException(AiCallException.AI_ERROR, "AI 응답이 길이 제한으로 잘렸습니다");
        }
        if (content.isEmpty()) {
            throw new AiCallException(AiCallException.AI_ERROR, "AI 응답이 비어 있습니다");
        }

        try {
            return objectMapper.readTree(content.toString());
        } catch (JacksonException e) {
            // 스트리밍 조각이 다 왔는데도 JSON 이 아니면 반쯤 만들어진 것을 저장하지 않는다
            throw new AiCallException(AiCallException.AI_ERROR, "AI 응답을 JSON 으로 읽지 못했습니다", e);
        }
    }

    private static void append(StringBuilder target, JsonNode node) {
        if (node != null && node.isTextual()) {
            target.append(node.asText());
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /** 오류 본문에 키가 섞여 올 이유는 없지만, 로그를 길게 남기지 않는다. */
    private static String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500) + "…";
    }
}
