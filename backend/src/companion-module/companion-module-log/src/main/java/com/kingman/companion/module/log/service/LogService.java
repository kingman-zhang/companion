package com.kingman.companion.module.log.service;

import com.kingman.companion.module.log.req.DailyLogReq;
import com.kingman.companion.module.log.resp.DailyLogHistoryResp;
import com.kingman.companion.module.log.resp.DailyLogResp;

import java.util.List;

public interface LogService {

    /** 提交今日日志（每日仅限一次） */
    DailyLogResp submit(DailyLogReq req);

    /** 获取今日日志（无记录返回 null） */
    DailyLogResp getToday();

    /** 获取历史日志列表（最多 30 条） */
    List<DailyLogHistoryResp> listHistory();

    /** 获取或生成 AI 建议（结果缓存到日志记录中） */
    String getSuggestion(String logId);
}
