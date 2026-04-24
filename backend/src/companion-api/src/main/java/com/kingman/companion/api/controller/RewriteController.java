package com.kingman.companion.api.controller;

import com.kingman.companion.framework.annotation.SkipCheckLoginAuth;
import com.kingman.companion.framework.common.IResult;
import com.kingman.companion.module.rewrite.req.RewriteReq;
import com.kingman.companion.module.rewrite.resp.RewriteResp;
import com.kingman.companion.module.rewrite.service.RewriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息改写接口
 * POST /api/v1/rewrite
 */
@RestController
@RequestMapping("/api/v1/rewrite")
@RequiredArgsConstructor
public class RewriteController {

    private final RewriteService rewriteService;

    /**
     * 提交改写请求
     */
    @PostMapping
    @SkipCheckLoginAuth
    public IResult<RewriteResp> rewrite(@Valid @RequestBody RewriteReq req) {
        return IResult.success(rewriteService.rewrite(req));
    }
}
