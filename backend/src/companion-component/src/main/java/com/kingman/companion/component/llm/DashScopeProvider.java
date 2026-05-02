package com.kingman.companion.component.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * 阿里云百炼（DashScope）OpenAI 兼容接口 Provider
 *
 * <p>实现 {@link LlmProvider}，provider 标识为 {@code "dashscope"}。
 * 使用 OpenAI 兼容格式（/v1/chat/completions），系统提示作为 role=system 的第一条消息传入。
 *
 * <p>Qwen3 系列模型在思考模式下会输出 {@code <think>...</think>} 块，
 * 本类在解析时自动剥离，只返回实际回复内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeProvider implements LlmProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DashScopeProperties properties;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public boolean supports(String provider) {
        return "dashscope".equalsIgnoreCase(provider);
    }

    @Override
    public String call(String systemPrompt, List<LlmMessage> messages, ModelConfig config) throws Exception {
        String requestBody = buildRequestBody(systemPrompt, messages, config);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("DashScope API error: status={}, model={}, body={}",
                    response.statusCode(), config.modelId(), response.body());
            throw new RuntimeException("DashScope API error: " + response.statusCode());
        }

        return extractText(response.body());
    }

    // ── 请求构造 ──────────────────────────────────────────────────────────────

    private String buildRequestBody(String systemPrompt,
                                    List<LlmMessage> messages,
                                    ModelConfig config) throws Exception {
        ObjectNode body = MAPPER.createObjectNode()
                .put("model", config.modelId())
                .put("max_tokens", config.maxTokens());

        ArrayNode msgsNode = MAPPER.createArrayNode();

        // OpenAI 兼容格式：system prompt 作为第一条 role=system 消息
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            msgsNode.add(MAPPER.createObjectNode()
                    .put("role", "system")
                    .put("content", systemPrompt));
        }

        for (LlmMessage msg : messages) {
            msgsNode.add(MAPPER.createObjectNode()
                    .put("role", msg.role())
                    .put("content", msg.content()));
        }

        body.set("messages", msgsNode);
        return MAPPER.writeValueAsString(body);
    }

    // ── 响应解析 ──────────────────────────────────────────────────────────────

    private String extractText(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.error("DashScope 响应缺少 choices: {}", responseBody);
            throw new RuntimeException("Empty choices in DashScope response");
        }
        String content = choices.get(0).path("message").path("content").asText();
        if (content.isBlank()) {
            log.error("DashScope 返回空内容: {}", responseBody);
            throw new RuntimeException("Empty content in DashScope response");
        }
        // Qwen3 思考模式会在内容前产生 <think>...</think> 块，剥离后返回实际回复
        return stripThinkingBlocks(content);
    }

    /**
     * 剥离 Qwen3 思考模式产生的 {@code <think>...</think>} 块。
     */
    private String stripThinkingBlocks(String text) {
        return text.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
    }
}
