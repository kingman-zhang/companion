# CLAUDE.md · RelationshipAI 项目上下文

> 这个文件是给 Claude Code 读的项目上下文。
> 每次在此项目目录启动 Claude Code，它会自动读取本文件。

---

## 项目简介

**RelationshipAI** 是一款面向 22-38 岁用户、聚焦关系危机场景的 AI 情感陪伴产品。
核心流程：情绪急救 → 关系评估 → 消息改写。

这不是泛聊天产品，也不是心理治疗工具。定位是：帮用户在关系危机时刻冷静下来、做出更理性的判断和表达。

---

## 项目文档索引

| 文件 | 内容 | 优先读取场景 |
|---|---|---|
| `prd.md` | 完整产品需求，含 Page Spec（第十三节）| 了解页面结构和组件 |
| `frd.md` | 功能需求细节，含验收标准（AC）| 了解交互逻辑和边界 |
| `prototype-brief.md` | 原型交付简版，含 MVP 范围和设计方向 | 了解开发优先级 |
| `ui-spec.md` | UI 规格（配色/字体/间距/组件规格）| 生成任何 UI 时必读 |

---

## MVP 开发范围（第一版只做这些）

| 页面 | 路由 | 状态 |
|---|---|---|
| P0 首页 / 状态选择 | `/` | MVP |
| P1 问卷页 / 7题单步表单 | `/questionnaire` | MVP |
| P2 评估结果页 | `/assessment/result` | MVP |
| P3 情绪急救聊天页 | `/chat` | MVP |
| P4 消息改写页 | `/rewrite` | MVP |
| P8 安全拦截页 | `/safety` | MVP |

**第一版不开发**：P5 挽回计划、P6 日志、P7 放下模式增强页。

---

## 技术原则

1. **规则决定决策，LLM 只负责表达**
   - 评估分数、等级（红黄绿）、置信度 → 全部规则计算，不让 LLM 决定
   - AI 只输出：评估解释文字、聊天回复、改写候选

2. **安全优先**
   - 所有模块前置安全检测
   - 触发 abuse_flags → 直接跳安全页，不继续任何流程
   - HTTP 451 = 安全拦截响应码

3. **Paywall 边界**
   - 聊天：免费 10 轮
   - 改写：免费每日 1 次，变体 2/3 毛玻璃遮罩
   - 计划：Day 1 免费，Day 2-7 遮罩（Phase 2）

---

## 设计规范摘要（详见 ui-spec.md）

```
背景色：#F5F3F0
强调色：#4A7B7B
字体：PingFang SC + DM Sans
卡片圆角：16px
页面 padding：20px
按钮高度：52px
```

状态色：
- Green：`#3D7A5A` / bg `#EDF6F0`
- Yellow：`#A07830` / bg `#FDF6E3`
- Red：`#9B4040` / bg `#FBF0F0`

---

## 关键数据模型（速查）

```typescript
// 评估结果
Assessment {
  score: number          // 0-100，规则计算
  level: 'red' | 'yellow' | 'green'
  confidence: number     // 0-1
  llm_reason: string     // AI 生成，≤300字
  recommended_action: enum
}

// 改写变体
RewriteVariant {
  version: 'gentle' | 'direct' | 'brief'
  content: string
  risk_level: 'low' | 'medium' | 'high'
  send_recommended: boolean
  confidence: number
}

// 聊天消息
Message {
  role: 'user' | 'assistant'
  content: string        // ≤2000字
  emotion_label: enum
  emotion_intensity: number  // 0-10
  safety_flag: boolean
}
```

---

## 评估评分规则（核心逻辑）

```
因子权重：
  relationship_duration  0.15
  contact_status         0.15
  who_initiated          0.10
  infidelity_present     0.20
  abuse_flags            0.25
  time_since_incident    0.15

分级：
  Green  ≥ 65
  Yellow  35-64
  Red    < 35 或触发 OVERRIDE

OVERRIDE_RED 条件（直接红，不计分）：
  - 家暴
  - 明确暴力威胁
  - 自伤/自杀风险
  - 严重控制或跟踪

置信度 = 输入完整率 × 关键项明确度 × 枚举合法率
user_primary_intent 不参与评分，只影响结果页 CTA 和流向
```

---


---

## 复用组件清单（优先建立）

| 组件名 | 复用页面 | 说明 |
|---|---|---|
| `StateLabel` | P2、P3、P4 | 红黄绿状态徽章 |
| `SafetyInterstitial` | P3、P4 | 全屏安全覆盖层 |
| `MicroInterventionCard` | P3 | 微干预卡片（emotion_intensity ≥ 8 触发）|
| `RewriteVariantCard` | P4 | 改写版本卡片 |
| `PaywallBanner` | P3、P4 | 付费拦截横条 |
| `AITextBlock` | P2、P6 | AI 生成只读文本块 |

---

## 当前阶段任务（给 Claude Code 的第一步）

**你现在的任务是：生成高保真 HTML 原型，逐页输出。**

执行顺序：
1. 读取 `ui-spec.md` 了解设计规范
2. 读取 `prd.md` 第十三节对应页面的组件列表
3. 按 ui-spec.md 第七节的"原型生成指令模板"生成每个页面
4. 每页单独输出为 `prototype/P0-home.html`、`prototype/P1-questionnaire.html` 等

从 P0 首页开始，逐页确认后再继续下一页。

---

## 上下文来源说明

本文件整理自 Claude.ai 会话（2026.04），基于以下三个 AI 生成文档：
- `prd.md` v1.0
- `frd.md` v1.1  
- `prototype-brief.md` v1.0

如有文档冲突，以 `frd.md` 为准。