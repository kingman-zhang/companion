package com.kingman.companion.module.log.service.impl;

import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.security.AuthContext;
import com.kingman.companion.module.log.entity.DailyLog;
import com.kingman.companion.module.log.repository.DailyLogRepository;
import com.kingman.companion.module.log.req.DailyLogReq;
import com.kingman.companion.module.log.resp.DailyLogHistoryResp;
import com.kingman.companion.module.log.resp.DailyLogResp;
import com.kingman.companion.module.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    private static final String SUGGESTION_SYSTEM_PROMPT =
            "你是一位温和的情感陪伴助手。根据用户今日情绪日志给出一条简短、温暖的鼓励或建议，" +
            "直接说建议内容，不超过60字，语气亲切，不分析不评判。";

    private final DailyLogRepository logRepository;
    private final LlmGateway llmGateway;

    @Override
    public DailyLogResp submit(DailyLogReq req) {
        String userId = AuthContext.getCurrentUserId();
        LocalDate today = LocalDate.now();

        if (userId != null) {
            boolean alreadySubmitted = logRepository
                    .findByUserIdAndLogDateAndDeletedFalse(userId, today)
                    .isPresent();
            if (alreadySubmitted) {
                throw new ApiException(CodeEnum.LOG_ALREADY_SUBMITTED);
            }
        }

        DailyLog log = new DailyLog();
        log.setUserId(userId);
        log.setLogDate(today);
        log.setEmotionScore(req.getEmotionScore());
        log.setEmotionLabels(req.getEmotionLabels());
        log.setContactedEx(req.isContactedEx());
        log.setContactOutcome(req.isContactedEx() ? req.getContactOutcome() : null);
        log.setNotes(req.getNotes());

        DailyLog saved = logRepository.save(log);
        return toResp(saved);
    }

    @Override
    public DailyLogResp getToday() {
        String userId = AuthContext.getCurrentUserId();
        if (userId == null) return null;
        return logRepository
                .findByUserIdAndLogDateAndDeletedFalse(userId, LocalDate.now())
                .map(this::toResp)
                .orElse(null);
    }

    @Override
    public List<DailyLogHistoryResp> listHistory() {
        String userId = AuthContext.getCurrentUserId();
        if (userId == null) return List.of();
        return logRepository
                .findTop30ByUserIdAndDeletedFalseOrderByLogDateDesc(userId)
                .stream()
                .map(this::toHistoryResp)
                .toList();
    }

    @Override
    public String getSuggestion(String logId) {
        DailyLog log = logRepository.findById(logId)
                .orElseThrow(() -> new ApiException(CodeEnum.NOT_FOUND));

        // 验证归属（不匹配时返回 NOT_FOUND，避免信息泄露）
        String userId = AuthContext.getCurrentUserId();
        if (userId != null && !userId.equals(log.getUserId())) {
            throw new ApiException(CodeEnum.NOT_FOUND);
        }

        // 已有缓存直接返回
        if (log.getAiSuggestion() != null && !log.getAiSuggestion().isBlank()) {
            return log.getAiSuggestion();
        }

        // 生成并缓存
        try {
            String userPrompt = buildSuggestionPrompt(log);
            String suggestion = llmGateway.complete(
                    SUGGESTION_SYSTEM_PROMPT,
                    userPrompt,
                    RoutingContext.chat(userPrompt.length(), null)
            );
            String trimmed = suggestion.trim();
            log.setAiSuggestion(trimmed);
            logRepository.save(log);
            return trimmed;
        } catch (Exception e) {
            logger.error("AI 建议生成失败 logId={}", logId, e);
            throw new ApiException(CodeEnum.AI_SERVICE_UNAVAILABLE);
        }
    }

    private String buildSuggestionPrompt(DailyLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append("情绪评分：").append(log.getEmotionScore()).append("/10，");
        sb.append("情绪：").append(String.join("、", log.getEmotionLabels())).append("。");
        if (log.isContactedEx()) {
            sb.append("今日有联系对方，结果：").append(
                    log.getContactOutcome() != null ? log.getContactOutcome() : "未说明").append("。");
        } else {
            sb.append("今日未联系对方。");
        }
        if (log.getNotes() != null && !log.getNotes().isBlank()) {
            sb.append("备注：").append(log.getNotes());
        }
        return sb.toString();
    }

    private DailyLogResp toResp(DailyLog log) {
        return DailyLogResp.builder()
                .logId(log.getId())
                .logDate(log.getLogDate())
                .emotionScore(log.getEmotionScore())
                .emotionLabels(log.getEmotionLabels())
                .contactedEx(log.isContactedEx())
                .contactOutcome(log.getContactOutcome())
                .notes(log.getNotes())
                .aiSuggestion(log.getAiSuggestion())
                .createdAt(log.getCreateTime())
                .build();
    }

    private DailyLogHistoryResp toHistoryResp(DailyLog log) {
        return DailyLogHistoryResp.builder()
                .logId(log.getId())
                .logDate(log.getLogDate())
                .emotionScore(log.getEmotionScore())
                .emotionLabels(log.getEmotionLabels())
                .contactedEx(log.isContactedEx())
                .contactOutcome(log.getContactOutcome())
                .notes(log.getNotes())
                .aiSuggestion(log.getAiSuggestion())
                .build();
    }
}
