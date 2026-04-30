package com.kingman.companion.component.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Anthropic Messages API 实现
 *
 * <p>使用 JDK 内置 {@link HttpClient}（Java 11+），无额外依赖。
 * 独立的 {@link ObjectMapper} 实例避免受全局 SNAKE_CASE 策略影响，
 * 确保请求体字段名与 Anthropic API 规范严格一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnthropicClientImpl implements AnthropicClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnthropicProperties properties;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    // ── 核心实现 ─────────────────────────────────────────────────────────────

    @Override
    public String completeWithHistory(String systemPrompt, List<AnthropicMessage> messages) {
        try {
            String requestBody = buildRequestBody(systemPrompt, messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/v1/messages"))
                    .header("x-api-key", properties.getApiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Anthropic API error: status={}, body={}",
                        response.statusCode(), response.body());
                throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
            }

            return extractTextContent(response.body());

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic API call failed", e);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
    }

    // ── 请求构造 ──────────────────────────────────────────────────────────────

    private String buildRequestBody(String systemPrompt,
                                    List<AnthropicMessage> messages) throws Exception {
        ObjectNode body = MAPPER.createObjectNode()
                .put("model", properties.getModel())
                .put("max_tokens", properties.getMaxTokens())
                .put("system", systemPrompt);

        ArrayNode msgsNode = MAPPER.createArrayNode();
        for (AnthropicMessage msg : messages) {
            msgsNode.add(MAPPER.createObjectNode()
                    .put("role", msg.role())
                    .put("content", msg.content()));
        }
        body.set("messages", msgsNode);

        return MAPPER.writeValueAsString(body);
    }

    // ── 响应解析 ──────────────────────────────────────────────────────────────

    private String extractTextContent(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        String text = root.path("content").path(0).path("text").asText();
        if (text.isEmpty()) {
            log.error("Empty text content in Anthropic response: {}", responseBody);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
        return text;
    }
}
