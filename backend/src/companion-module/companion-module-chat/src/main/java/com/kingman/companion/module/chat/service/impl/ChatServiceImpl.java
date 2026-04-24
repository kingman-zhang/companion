package com.kingman.companion.module.chat.service.impl;

import com.kingman.companion.component.enums.EmotionLabel;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.util.DistributeID;
import com.kingman.companion.module.chat.entity.ChatMessage;
import com.kingman.companion.module.chat.entity.ChatSession;
import com.kingman.companion.module.chat.repository.ChatMessageRepository;
import com.kingman.companion.module.chat.repository.ChatSessionRepository;
import com.kingman.companion.module.chat.req.ChatReq;
import com.kingman.companion.module.chat.resp.ChatResp;
import com.kingman.companion.module.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天服务实现
 * MVP 阶段：LLM 调用使用占位实现，返回模板回复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int FREE_TIER_ROUND_LIMIT = 10;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    @Override
    public ChatResp sendMessage(ChatReq req) {
        // 加载会话
        ChatSession session = sessionRepository.findByIdAndDeletedFalse(req.getSessionId())
                .orElseThrow(() -> new ApiException(CodeEnum.NOT_FOUND));

        // 免费层轮次限制
        if (session.getRoundCount() >= FREE_TIER_ROUND_LIMIT) {
            throw new ApiException(CodeEnum.FREE_TIER_LIMIT_REACHED);
        }

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setId(DistributeID.generate());
        userMsg.setSessionId(req.getSessionId());
        userMsg.setRole("user");
        userMsg.setContent(req.getContent());
        userMsg.setSafetyFlag(false);
        messageRepository.save(userMsg);

        // 加载最近 10 条历史（用于 LLM 上下文，MVP 中暂未接入）
        List<ChatMessage> history = messageRepository.findTop10BySessionIdAndDeletedFalse(
                req.getSessionId(), Sort.by(Sort.Direction.DESC, "createTime"));

        // 情绪检测（MVP 使用简单规则）
        EmotionLabel emotionLabel = detectEmotion(req.getContent());
        int emotionIntensity = detectIntensity(req.getContent());

        // AI 回复（MVP 使用模板）
        String aiContent = generateReply(emotionLabel, emotionIntensity);

        // 保存 AI 消息
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setId(DistributeID.generate());
        aiMsg.setSessionId(req.getSessionId());
        aiMsg.setRole("assistant");
        aiMsg.setContent(aiContent);
        aiMsg.setEmotionLabel(emotionLabel);
        aiMsg.setEmotionIntensity(emotionIntensity);
        aiMsg.setSafetyFlag(false);
        ChatMessage savedAiMsg = messageRepository.save(aiMsg);

        // 更新会话轮次
        session.setRoundCount(session.getRoundCount() + 1);
        sessionRepository.save(session);

        ChatResp.ChatRespBuilder builder = ChatResp.builder()
                .messageId(savedAiMsg.getId())
                .sessionId(req.getSessionId())
                .role("assistant")
                .content(aiContent)
                .emotionLabel(emotionLabel)
                .emotionIntensity(emotionIntensity)
                .safetyFlag(false)
                .createdAt(savedAiMsg.getCreateTime());

        // 微干预触发规则（intensity ≥ 8）
        if (emotionIntensity >= 8) {
            builder.microIntervention(buildMicroIntervention(emotionLabel));
        }

        return builder.build();
    }

    @Override
    public String createSession() {
        ChatSession session = new ChatSession();
        session.setId(DistributeID.generate());
        session.setRoundCount(0);
        session.setInCooldown(false);
        ChatSession saved = sessionRepository.save(session);
        return saved.getId();
    }

    private EmotionLabel detectEmotion(String content) {
        if (content.contains("焦虑") || content.contains("担心") || content.contains("害怕")) {
            return EmotionLabel.ANXIETY;
        }
        if (content.contains("愤怒") || content.contains("生气") || content.contains("气死")) {
            return EmotionLabel.ANGER;
        }
        if (content.contains("难过") || content.contains("伤心") || content.contains("哭")) {
            return EmotionLabel.SADNESS;
        }
        return EmotionLabel.OTHER;
    }

    private int detectIntensity(String content) {
        int intensity = 5;
        long exclamations = content.chars().filter(c -> c == '！' || c == '!').count();
        long ellipsis = content.chars().filter(c -> c == '…').count();
        intensity += (int) Math.min(exclamations * 2, 4);
        if (content.length() > 500) intensity += 1;
        return Math.min(intensity, 10);
    }

    private String generateReply(EmotionLabel emotion, int intensity) {
        if (intensity >= 8) {
            return "我听到你了，你现在的感受非常真实。先让自己的情绪有一个出口，不用急着做任何决定。";
        }
        return switch (emotion) {
            case ANXIETY -> "感觉到你很担心。不确定的感觉确实很煎熬，我们可以一步步来梳理一下情况。";
            case ANGER -> "愤怒说明你在乎，这是完全正常的。先不要发那条消息，等情绪稳定后再做决定会更好。";
            case SADNESS -> "难过的时候最重要的是照顾好自己。你现在感觉怎么样？";
            default -> "谢谢你愿意跟我分享。能多说一点是什么让你现在最揪心吗？";
        };
    }

    private ChatResp.MicroIntervention buildMicroIntervention(EmotionLabel emotion) {
        return switch (emotion) {
            case ANXIETY -> ChatResp.MicroIntervention.builder()
                    .type("breathe")
                    .title("先深呼吸一下")
                    .build();
            case ANGER -> ChatResp.MicroIntervention.builder()
                    .type("step_away")
                    .title("先离开这里一会儿")
                    .build();
            default -> ChatResp.MicroIntervention.builder()
                    .type("delay_send")
                    .title("先别发那条消息")
                    .actionLabel("帮我改写它")
                    .actionTarget("/rewrite")
                    .build();
        };
    }
}
