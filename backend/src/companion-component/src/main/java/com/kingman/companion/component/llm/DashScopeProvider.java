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
import java.util.function.Consumer;

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
        String requestBody = buildRequestBody(systemPrompt, messages, config, false);

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

    @Override
    public void callStream(String systemPrompt, List<LlmMessage> messages, ModelConfig config,
                           Consumer<String> onChunk) throws Exception {
        String requestBody = buildRequestBody(systemPrompt, messages, config, true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
          String body = response.body().reduce("", (a, b) -> a + b + "\n");
          log.error("DashScope stream error: status={}, model={}, body={}",
                  response.statusCode(), config.modelId(), body);
          throw new RuntimeException("DashScope stream error: " + response.statusCode());
        }

        response.body().forEach(line -> {
            if (!line.startsWith("data:")) return;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) return;
            try {
                JsonNode root = MAPPER.readTree(data);
                JsonNode delta = root.path("choices").path(0).path("delta");
                String content = delta.path("content").asText("");
                if (!content.isEmpty()) onChunk.accept(content);
            } catch (Exception e) {
                log.debug("DashScope stream parse skip: {}", e.getMessage());
            }
        });
    }

    // ── 请求构造 ──────────────────────────────────────────────────────────────

    private String buildRequestBody(String systemPrompt,
                                    List<LlmMessage> messages,
                                    ModelConfig config) throws Exception {
        return buildRequestBody(systemPrompt, messages, config, false);
    }

    private String buildRequestBody(String systemPrompt,
                                    List<LlmMessage> messages,
                                    ModelConfig config,
                                    boolean stream) throws Exception {
        ObjectNode body = MAPPER.createObjectNode()
                .put("model", config.modelId())
                .put("max_tokens", config.maxTokens())
                .put("stream", stream);

        // 非流式接口仍要求 JSON 输出；流式接口需要返回纯文本 + 分隔符，不能强压 JSON object。
        if (!stream) {
            body.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
        }

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
