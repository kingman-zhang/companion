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
import java.util.stream.Collectors;

/**
 * Anthropic Messages API Provider
 *
 * <p>实现 {@link LlmProvider}，provider 标识为 {@code "anthropic"}。
 * 每次调用使用 {@link ModelConfig} 中的 model-id、max-tokens、timeout，
 * 连接配置来自 {@link AnthropicProperties}（api-key、base-url、connect-timeout）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnthropicProvider implements LlmProvider {

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

    @Override
    public boolean supports(String provider) {
        return "anthropic".equalsIgnoreCase(provider);
    }

    @Override
    public String call(String systemPrompt, List<LlmMessage> messages, ModelConfig config) throws Exception {
        String requestBody = buildRequestBody(systemPrompt, messages, config);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/v1/messages"))
                .header("x-api-key", properties.getApiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Anthropic API error: status={}, model={}, body={}",
                    response.statusCode(), config.modelId(), response.body());
            throw new RuntimeException("Anthropic API error: " + response.statusCode());
        }

        return extractText(response.body());
    }

    @Override
    public void callStream(String systemPrompt, List<LlmMessage> messages, ModelConfig config,
                           Consumer<String> onChunk) throws Exception {
        String requestBody = buildRequestBody(systemPrompt, messages, config, true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/v1/messages"))
                .header("x-api-key", properties.getApiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<java.util.stream.Stream<String>> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200) {
            String body = response.body().collect(Collectors.joining("\n"));
            log.error("Anthropic stream error: status={}, model={}, body={}",
                    response.statusCode(), config.modelId(), body);
            throw new RuntimeException("Anthropic stream error: " + response.statusCode());
        }

        response.body().forEach(line -> {
            if (!line.startsWith("data: ")) return;
            String data = line.substring(6).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) return;
            try {
                JsonNode node = MAPPER.readTree(data);
                if ("content_block_delta".equals(node.path("type").asText())) {
                    String text = node.path("delta").path("text").asText("");
                    if (!text.isEmpty()) onChunk.accept(text);
                }
            } catch (Exception e) {
                log.debug("Anthropic stream parse skip: {}", e.getMessage());
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
                .put("stream", stream)
                .put("system", systemPrompt);

        ArrayNode msgsNode = MAPPER.createArrayNode();
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
        String text = root.path("content").path(0).path("text").asText();
        if (text.isEmpty()) {
            log.error("Empty text in Anthropic response: {}", responseBody);
            throw new RuntimeException("Empty response from Anthropic");
        }
        return text;
    }
}
