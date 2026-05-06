package com.kingman.companion.module.rewrite.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * 改写每日使用量跟踪（按用户 + 日期）
 *
 * <p>免费层每日 1 次限制的计数凭据，无需全量审计字段。
 */
@Data
@Document(collection = "rewrite_daily_usage")
@CompoundIndex(name = "idx_user_date", def = "{'userId':1,'usageDate':1}", unique = true)
public class RewriteDailyUsage {

    @Id
    private String id;

    private String userId;

    private LocalDate usageDate;

    /** 当日已使用次数 */
    private int count;
}
