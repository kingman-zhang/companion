package com.kingman.companion.module.log.service.impl;

import com.kingman.companion.component.llm.LlmGateway;
import com.kingman.companion.component.llm.RoutingContext;
import com.kingman.companion.framework.common.CodeEnum;
import com.kingman.companion.framework.exception.ApiException;
import com.kingman.companion.framework.security.AuthContext;
import com.kingman.companion.module.log.config.LogProperties;
import com.kingman.companion.module.log.entity.AssessmentSummary;
import com.kingman.companion.module.log.entity.DailyLog;
import com.kingman.companion.module.log.entity.UserFeedback;
import com.kingman.companion.module.log.repository.AssessmentSummaryRepository;
import com.kingman.companion.module.log.repository.DailyLogRepository;
import com.kingman.companion.module.log.req.DailyLogReq;
import com.kingman.companion.module.log.req.UserFeedbackReq;
import com.kingman.companion.module.log.resp.DailyLogHistoryResp;
import com.kingman.companion.module.log.resp.DailyLogResp;
import com.kingman.companion.module.log.resp.UserFeedbackResp;
import com.kingman.companion.module.log.repository.UserFeedbackRepository;
import com.kingman.companion.module.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogServiceImpl.class);

    private final DailyLogRepository logRepository;
    private final AssessmentSummaryRepository assessmentSummaryRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final LlmGateway llmGateway;
    private final LogProperties logProperties;

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
        log.setContactOutcomeNote(req.isContactedEx() ? req.getContactOutcomeNote() : null);
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
            AssessmentSummary assessment = userId != null
                    ? assessmentSummaryRepository.findFirstByUserIdOrderByIdDesc(userId).orElse(null)
                    : null;
            String userPrompt = buildSuggestionPrompt(log, assessment);
            String suggestion = llmGateway.complete(
                    logProperties.getSuggestionSystemPrompt(),
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

    @Override
    public UserFeedbackResp submitFeedback(UserFeedbackReq req) {
        UserFeedback feedback = new UserFeedback();
        feedback.setType(req.getType());
        feedback.setContent(req.getContent().trim());
        feedback.setContact(trimToNull(req.getContact()));
        feedback.setSourcePage(trimToNull(req.getSourcePage()));
        feedback.setUserId(AuthContext.getCurrentUserId());

        UserFeedback saved = userFeedbackRepository.save(feedback);
        return UserFeedbackResp.builder()
                .feedbackId(saved.getId())
                .type(saved.getType())
                .content(saved.getContent())
                .contact(saved.getContact())
                .createdAt(saved.getCreateTime())
                .build();
    }

    private String buildSuggestionPrompt(DailyLog log, AssessmentSummary assessment) {
        StringBuilder sb = new StringBuilder();

        // 关系评估背景（如有）
        if (assessment != null) {
            sb.append("【用户关系背景】");
            if (StringUtils.hasText(assessment.getLevel())) {
                String levelDesc = switch (assessment.getLevel()) {
                    case "GREEN"  -> "关系相对乐观";
                    case "YELLOW" -> "关系需谨慎";
                    case "RED"    -> "关系需重点关注";
                    default       -> assessment.getLevel();
                };
                sb.append("评估结论：").append(levelDesc).append("。");
            }
            if (StringUtils.hasText(assessment.getCoreInsight())) {
                sb.append("核心洞察：「").append(assessment.getCoreInsight()).append("」。");
            }
            sb.append("\n");
        }

        // 今日日志
        sb.append("【今日记录】");
        sb.append("情绪评分：").append(log.getEmotionScore()).append("/10，");
        sb.append("情绪：").append(String.join("、", log.getEmotionLabels())).append("。");
        if (log.isContactedEx()) {
            String outcome = log.getContactOutcome() != null ? log.getContactOutcome() : "未说明";
            sb.append("今日有联系对方，结果：").append(outcome).append("。");
            if (StringUtils.hasText(log.getContactOutcomeNote())) {
                sb.append("补充描述：").append(log.getContactOutcomeNote()).append("。");
            }
        } else {
            sb.append("今日未联系对方。");
        }
        if (StringUtils.hasText(log.getNotes())) {
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
                .contactOutcomeNote(log.getContactOutcomeNote())
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
                .contactOutcomeNote(log.getContactOutcomeNote())
                .notes(log.getNotes())
                .aiSuggestion(log.getAiSuggestion())
                .build();
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) return null;
        return text.trim();
    }
}
