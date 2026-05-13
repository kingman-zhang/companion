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
 * OpenAI Chat Completions Provider。
 *
 * <p>实现 {@link LlmProvider}，provider 标识为 {@code "openai"}。
 * 使用官方 {@code /v1/chat/completions} 接口，system prompt 作为第一条
 * {@code role=system} 消息传入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIProvider implements LlmProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAIProperties properties;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public boolean supports(String provider) {
        return "openai".equalsIgnoreCase(provider);
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
            log.error("OpenAI API error: status={}, model={}, body={}",
                    response.statusCode(), config.modelId(), response.body());
            throw new RuntimeException("OpenAI API error: " + response.statusCode());
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
            log.error("OpenAI stream error: status={}, model={}, body={}",
                    response.statusCode(), config.modelId(), body);
            throw new RuntimeException("OpenAI stream error: " + response.statusCode());
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
                log.debug("OpenAI stream parse skip: {}", e.getMessage());
            }
        });
    }

    String buildRequestBody(String systemPrompt,
                            List<LlmMessage> messages,
                            ModelConfig config,
                            boolean stream) throws Exception {
        ObjectNode body = MAPPER.createObjectNode()
                .put("model", config.modelId())
                .put("max_tokens", config.maxTokens())
                .put("stream", stream);

        if (!stream) {
            body.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
        }

        ArrayNode msgsNode = MAPPER.createArrayNode();
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

    String extractText(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.error("OpenAI 响应缺少 choices: {}", responseBody);
            throw new RuntimeException("Empty choices in OpenAI response");
        }
        String content = choices.get(0).path("message").path("content").asText();
        if (content.isBlank()) {
            log.error("OpenAI 返回空内容: {}", responseBody);
            throw new RuntimeException("Empty content in OpenAI response");
        }
        return content.trim();
    }
}
