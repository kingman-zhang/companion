package com.kingman.companion.component.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAIProviderTest {

    private final OpenAIProvider provider = new OpenAIProvider(new OpenAIProperties());

    @Test
    void supports_returns_true_for_openai() {
        assertThat(provider.supports("openai")).isTrue();
        assertThat(provider.supports("OPENAI")).isTrue();
        assertThat(provider.supports("dashscope")).isFalse();
    }

    @Test
    void buildRequestBody_includes_system_messages_and_json_response_format_for_non_stream() throws Exception {
        String body = provider.buildRequestBody(
                "system prompt",
                List.of(LlmMessage.user("hello")),
                new ModelConfig("openai", "chat-latest", 1024, 30),
                false
        );

        assertThat(body).contains("\"model\":\"chat-latest\"");
        assertThat(body).contains("\"max_tokens\":1024");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"response_format\":{\"type\":\"json_object\"}");
        assertThat(body).contains("\"role\":\"system\"");
        assertThat(body).contains("\"content\":\"system prompt\"");
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("\"content\":\"hello\"");
    }

    @Test
    void buildRequestBody_does_not_force_json_response_format_for_stream() throws Exception {
        String body = provider.buildRequestBody(
                "system prompt",
                List.of(LlmMessage.user("hello")),
                new ModelConfig("openai", "chat-latest", 1024, 30),
                true
        );

        assertThat(body).contains("\"stream\":true");
        assertThat(body).doesNotContain("response_format");
    }

    @Test
    void extractText_returns_first_choice_content() throws Exception {
        String response = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\\"reply\\":\\"你好\\",\\"emotion_label\\":\\"CALM\\",\\"emotion_intensity\\":3}"
                      }
                    }
                  ]
                }
                """;

        assertThat(provider.extractText(response))
                .isEqualTo("{\"reply\":\"你好\",\"emotion_label\":\"CALM\",\"emotion_intensity\":3}");
    }

    @Test
    void extractText_throws_when_choices_missing() {
        assertThatThrownBy(() -> provider.extractText("{\"choices\":[]}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Empty choices");
    }
}
