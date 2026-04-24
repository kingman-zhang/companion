package com.kingman.companion.component.enums;

/**
 * Q7: 现在你最想要什么？（不参与评分，影响结果页 CTA 和流向）
 */
public enum UserPrimaryIntent {
    /** 挽回,重新在一起 — 将生成30天计划 */
    RECONCILE,
    /** 先处理情绪,再决定 — 将进入情绪急救 */
    PROCESS_EMOTION_FIRST,
    /** 学会好好告别 — 将生成告别话术 */
    LEARN_GOODBYE,
    /** 说不清,先聊聊 — 进入AI对话 */
    CHAT_FIRST
}
