package com.kingman.companion.api.controller;

import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.log.req.DailyLogReq;
import com.kingman.companion.module.log.resp.DailyLogHistoryResp;
import com.kingman.companion.module.log.resp.DailyLogResp;
import com.kingman.companion.module.log.service.LogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 每日情绪日志接口
 * POST /api/v1/log
 */
@RestController
@RequestMapping("/api/v1/log")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /** 提交今日日志 */
    @PostMapping
    @SkipCheckLoginAuth
    public IResult<DailyLogResp> submit(@Valid @RequestBody DailyLogReq req) {
        return IResult.success(logService.submit(req));
    }

    /** 获取今日日志（未提交返回 null） */
    @GetMapping("/today")
    @SkipCheckLoginAuth
    public IResult<DailyLogResp> getToday() {
        return IResult.success(logService.getToday());
    }

    /** 历史日志列表（最多 30 条） */
    @GetMapping("/history")
    @SkipCheckLoginAuth
    public IResult<List<DailyLogHistoryResp>> listHistory() {
        return IResult.success(logService.listHistory());
    }

    /** 获取或生成 AI 建议 */
    @GetMapping("/{logId}/suggestion")
    @SkipCheckLoginAuth
    public IResult<Map<String, String>> getSuggestion(@PathVariable String logId) {
        String suggestion = logService.getSuggestion(logId);
        return IResult.success(Map.of("suggestion", suggestion));
    }
}
