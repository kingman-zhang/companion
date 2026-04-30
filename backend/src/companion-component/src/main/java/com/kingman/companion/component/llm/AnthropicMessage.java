package com.kingman.companion.component.llm;

/**
 * Anthropic Messages API 单条消息（role + content）
 */
public record AnthropicMessage(String role, String content) {}
