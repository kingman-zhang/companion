package com.kingman.companion.module.rewrite.service;

import com.kingman.companion.module.rewrite.req.RewriteReq;
import com.kingman.companion.module.rewrite.resp.RewriteResp;

/**
 * 改写服务接口
 */
public interface RewriteService {

    /**
     * 执行消息改写，返回 3 个版本
     *
     * @param assessmentContext 评估上下文摘要（可为 null，表示无评估背景直接改写）
     */
    RewriteResp rewrite(RewriteReq req, String assessmentContext);
}
