package com.kingman.companion.module.log.entity;

import com.kingman.companion.framework.common.AbstractBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "daily_logs")
public class DailyLog extends AbstractBaseEntity {

    private String userId;
    private LocalDate logDate;

    /** 情绪评分 1-10 */
    private int emotionScore;

    /** 情绪标签：ANGER / SADNESS / GUILT / ANXIETY / FEAR / CALM */
    private List<String> emotionLabels;

    /** 是否联系了对方 */
    private boolean contactedEx;

    /** 联系结果：POSITIVE / NEUTRAL / NEGATIVE（contactedEx=true 时有值） */
    private String contactOutcome;

    /** 今日备注（可选，max 500 字） */
    private String notes;

    /** AI 建议（首次请求时生成，之后缓存） */
    private String aiSuggestion;
}
