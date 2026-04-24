# RelationshipAI API 接口文档（中文速查版）

> 版本：1.0.0 · 完整 Schema 见 `api-schema.yaml`
>
> **MVP 接口：** /assessment · /chat · /rewrite
> **Phase 2（已定义）：** /plan · /log

---

## 目录

1. [全局规则](#全局规则)
2. [错误码规范](#错误码规范)
3. [接口详情](#接口详情)
   - [POST /api/v1/assessment](#1-post-apiv1assessment--提交关系评估问卷)
   - [POST /api/v1/chat](#2-post-apiv1chat--发送聊天消息)
   - [POST /api/v1/rewrite](#3-post-apiv1rewrite--消息改写)
   - [GET /api/v1/plan](#4-get-apiv1plan--获取挽回计划phase-2)
   - [POST /api/v1/log](#5-post-apiv1log--提交每日日志phase-2)
   - [GET /api/v1/log](#6-get-apiv1log--获取日志历史phase-2)
4. [关键数据结构](#关键数据结构)
5. [枚举值速查表](#枚举值速查表)

---

## 全局规则

| 规则 | 说明 |
|---|---|
| 安全优先 | 任何接口均可触发 HTTP 451；前端收到 451 必须立即显示全屏安全覆盖层 |
| 规则 vs LLM | 评分、等级、置信度、recommended_action 全部由规则引擎计算，LLM 只生成文字 |
| 置信度 | 值域 0.0–1.0（不是百分比），< 0.4 时前端展示免责声明 |
| 冷却机制 | 用户点击「我明白了」后启动 30 分钟冷却，期间禁止新 session |
| 免费层限制 | 聊天 10 轮 / 改写每日 1 次 / 计划仅 Day 1（超出返回 429/402） |

---

## 错误码规范

| HTTP 状态码 | code 标识符 | 说明 |
|---|---|---|
| 200 | — | 成功 |
| 400 | MISSING_REQUIRED_FIELD / INVALID_REQUEST | 请求参数错误（缺字段、类型错误） |
| 402 | PLAN_PAYWALL | 需要付费解锁（仅 /plan）|
| 409 | LOG_ALREADY_SUBMITTED | 今日日志已提交（仅 POST /log）|
| 422 | INVALID_ENUM_VALUE | 字段校验失败（enum 非法、数值超范围）|
| 429 | FREE_TIER_LIMIT_REACHED | 超出免费层上限 |
| **451** | **SAFETY_BLOCKED** | **安全拦截（abuse_flags / 关键词 / 分类器）** |
| 500 | INTERNAL_SERVER_ERROR | 服务端内部异常 |
| 503 | AI_SERVICE_UNAVAILABLE | AI 服务不可用（LLM 超时）|

### 错误响应体（通用）

```json
{
  "code": "INVALID_ENUM_VALUE",
  "message": "字段 who_initiated 值非法",
  "detail": { "field": "who_initiated", "received": "nobody" }
}
```

### 451 安全拦截响应体

```json
{
  "code": "SAFETY_BLOCKED",
  "message": "检测到安全风险，已中断当前操作",
  "trigger_type": "abuse_flags",
  "session_cooldown_until": null
}
```

`trigger_type` 取值：`self_harm` / `violence` / `abuse_flags`

---

## 接口详情

### 1. POST /api/v1/assessment · 提交关系评估问卷

**MVP · 免费**

#### Request Body

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| session_id | string | 否 | 会话 ID |
| entry_state | EntryState | 否 | P0 情绪状态（埋点用，不参与评分）|
| relationship_duration | RelationshipDuration | **是** | Q1 交往时长（权重 0.15）|
| who_initiated | WhoInitiated | **是** | Q2 谁先提出分手（权重 0.10）|
| contact_status | ContactStatus | **是** | Q3 联系状态（权重 0.15）|
| infidelity_present | boolean | **是** | Q4 是否出轨（权重 0.20）|
| abuse_flags | AbuseFlag[] | **是** | Q5 安全风险事件多选（权重 0.25）⚠️ 非 none 触发 451 |
| user_primary_intent | UserPrimaryIntent | **是** | Q6 用户意图（不参与评分，影响流向）|
| time_since_incident | TimeSinceIncident | **是** | Q7 事件时间（权重 0.15）|

#### Response 200

| 字段 | 类型 | 说明 |
|---|---|---|
| assessment_id | string | 评估记录 ID |
| score | number | 总分 0–100（规则计算）|
| level | AssessmentLevel | red / yellow / green（规则计算）|
| confidence | number | 置信度 0.0–1.0（规则计算）|
| rule_factors | object | 六因子各自得分明细 |
| llm_reason | string | AI 解释文字，≤80 字 |
| recommended_action | RecommendedAction | 推荐行动枚举 |
| created_at | datetime | ISO 8601 |

**分级规则：**
- `green`：score ≥ 65
- `yellow`：35 ≤ score < 65
- `red`：score < 35，或 OVERRIDE_RED（家暴/暴力威胁/自伤自杀/严重控制跟踪）

**可能的响应码：** 200 / 400 / 422 / 451 / 500 / 503

---

### 2. POST /api/v1/chat · 发送聊天消息

**MVP · 免费 10 轮**

#### Request Body

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| session_id | string | **是** | 聊天会话 ID（后端加载最近 10 条历史作为上下文）|
| content | string | **是** | 用户消息，1–2000 字 |

#### Response 200

| 字段 | 类型 | 说明 |
|---|---|---|
| message_id | string | 消息 ID |
| session_id | string | 会话 ID |
| role | string | 固定 `assistant` |
| content | string | AI 回复，≤150 字 |
| emotion_label | EmotionLabel | 检测到的用户情绪 |
| emotion_intensity | number | 情绪强度 0–10 |
| safety_flag | boolean | 是否命中安全风险 |
| micro_intervention | MicroIntervention \| null | 微干预卡片（intensity ≥ 8 时返回）|
| created_at | datetime | — |

**微干预触发规则：**

| type | 触发条件 | 标题 | 次按钮 |
|---|---|---|---|
| breathe | emotion_label=anxiety 且 intensity≥8 | 先深呼吸一下 | — |
| step_away | emotion_label=anger 且 intensity≥8 | 先离开这里一会儿 | — |
| delay_send | 其他高强度情绪（intensity≥8）| 先别发那条消息 | 帮我改写它（→P4）|

**可能的响应码：** 200 / 400 / 422 / 429 / 451 / 500 / 503

---

### 3. POST /api/v1/rewrite · 消息改写

**MVP · 免费每日 1 次**

#### Request Body

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| session_id | string | 否 | 会话 ID（关联上下文）|
| original_message | string | **是** | 原始消息，10–1000 字 |

#### Response 200

| 字段 | 类型 | 说明 |
|---|---|---|
| rewrite_id | string | 改写记录 ID |
| variants | RewriteVariant[3] | 固定 3 个版本（gentle / direct / brief）|
| created_at | datetime | — |

**RewriteVariant 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| version | RewriteVersion | gentle / direct / brief |
| content | string | 改写内容，≤500 字（前端显示建议截断至 150 字）|
| risk_level | RiskLevel | low / medium / high |
| risk_reason | string | 风险说明，≤30 字 |
| send_recommended | boolean | 是否建议发送（risk_level=high 时强制 false）|
| confidence | number | 置信度 0.0–1.0 |

**付费遮罩规则：** 免费用户仅可查看 gentle 版本；direct / brief 在前端做毛玻璃遮罩处理（后端正常返回，前端根据 `subscription_tier` 控制显示）。

**可能的响应码：** 200 / 400 / 422 / 429 / 451 / 500 / 503

---

### 4. GET /api/v1/plan · 获取挽回计划（Phase 2）

**Phase 2 · 付费**

#### Query Parameters

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| assessment_id | string | **是** | 关联评估 ID |
| user_id | string | **是** | 用户 ID |

#### Response 200

| 字段 | 类型 | 说明 |
|---|---|---|
| plan_id | string | 计划 ID |
| assessment_id | string | 关联评估 ID |
| stage | PlanStage | 当前阶段 |
| current_stage_index | integer | 0–3（0=冷静期）|
| tasks | Task[] | 任务列表（免费层仅含 Day 1）|
| created_at / updated_at | datetime | — |

**Task 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| task_id | string | — |
| stage | PlanStage | 所属阶段 |
| day_number | integer | 第几天（1–7）|
| title | string | 任务标题，≤80 字 |
| description | string | 任务描述，≤300 字 |
| do_list | string[] | 建议行为，2–5 条 |
| dont_list | string[] | 禁忌行为，2–5 条 |
| completed | boolean | 是否完成 |
| completed_at | datetime \| null | 完成时间 |

**前提：** level ≠ red，且 user_primary_intent ≠ letgo

**可能的响应码：** 200 / 400 / 402 / 404 / 500 / 503

---

### 5. POST /api/v1/log · 提交每日日志（Phase 2）

**Phase 2 · 付费**

#### Request Body

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| date | string (date) | **是** | 日志日期 YYYY-MM-DD |
| emotion_score | integer | **是** | 情绪评分 1–10（1=很平静，10=情绪崩溃）|
| emotion_label | EmotionLabel | **是** | 主要情绪标签 |
| contacted_ex | boolean | **是** | 今天是否联系了对方 |
| contact_outcome | ContactOutcome | 否 | 联系结果（仅 contacted_ex=true 时有效）|
| notes | string | 否 | 当日备注，≤200 字 |

#### Response 200

| 字段 | 类型 | 说明 |
|---|---|---|
| log_id | string | 日志 ID |
| date | string | 日志日期 |
| ai_suggestion | string \| null | AI 建议，≤200 字 |
| created_at | datetime | — |

**可能的响应码：** 200 / 400 / 409 / 422 / 500 / 503

---

### 6. GET /api/v1/log · 获取日志历史（Phase 2）

**Phase 2 · 付费（trend 字段）**

#### Query Parameters

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| start_date | date | 否 | — | 起始日期 YYYY-MM-DD |
| end_date | date | 否 | — | 结束日期 YYYY-MM-DD |
| limit | integer | 否 | 30 | 每页数量（1–100）|
| offset | integer | 否 | 0 | 分页偏移量 |

#### Response 200

| 字段 | 类型 | 说明 |
|---|---|---|
| logs | EmotionLog[] | 日志列表，时间倒序 |
| total | integer | 总条数（用于分页）|
| trend | EmotionTrend \| null | 趋势分析（仅付费用户；免费用户为 null）|

**EmotionTrend 字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| period_days | integer | 统计周期天数 |
| average_score | number | 周期内平均情绪评分 |
| score_change | number | 与上周期相比变化值（正=上升）|
| summary | string \| null | LLM 趋势摘要，≤100 字 |

**可能的响应码：** 200 / 400 / 500

---

## 关键数据结构

### QuestionnaireInput（问卷输入）

7 题问卷全部字段必填，`abuse_flags` 任何非 `none` 值直接触发 451。

### AssessmentResult（评估结果）

```
score: 0–100（规则计算，非 LLM）
level: red | yellow | green（规则计算，非 LLM）
confidence: 0.0–1.0（规则计算 = 输入完整率 × 关键项明确度 × 枚举合法率）
llm_reason: string（≤80字，LLM 生成，仅表达，不决策）
recommended_action: enum（规则映射）
rule_factors: { relationship_duration, contact_status, who_initiated,
                infidelity_present, abuse_flags, time_since_incident }
```

### ChatMessage（AI 聊天回复）

```
role: "assistant"（固定）
content: string（≤150字）
emotion_label: EmotionLabel
emotion_intensity: 0–10（≥8 时附带 micro_intervention）
safety_flag: boolean
```

### RewriteVariant（改写变体）

```
version: "gentle" | "direct" | "brief"
content: string（≤500字）
risk_level: "low" | "medium" | "high"
risk_reason: string（≤30字）
send_recommended: boolean（risk_level=high 时强制 false）
confidence: 0.0–1.0
```

### MicroIntervention（微干预卡片）

```
type: "breathe" | "step_away" | "delay_send"
title: string
body: string
action_label: string（主按钮）
secondary_action_label: string | null（delay_send 才有，→ 跳 P4）
```

---

## 枚举值速查表

### EntryState（P0 情绪状态）

| 枚举值 | 中文显示 |
|---|---|
| just_dumped | 刚被分手，我很难受 |
| third_party | 发现对方有第三者 |
| near_breakup | 吵到快分手了，我想挽回 |
| just_confused | 我只是很乱，想找人说说 |

### RelationshipDuration（Q1 交往时长）

| 枚举值 | 中文显示 |
|---|---|
| less_than_6m | 半年以内 |
| 6m_to_2y | 半年到2年 |
| 2y_to_5y | 2到5年 |
| over_5y | 5年以上 |

### WhoInitiated（Q2 谁先提出）

| 枚举值 | 中文显示 |
|---|---|
| me | 是我 |
| partner | 是对方 |
| both | 双方都有 |
| unclear | 说不清楚 |

### ContactStatus（Q3 联系状态）

| 枚举值 | 中文显示 |
|---|---|
| no_contact | 完全不联系 |
| occasional | 偶尔联系 |
| normal | 正常联系 |
| living_together | 还住在一起 |

### AbuseFlag（Q5 安全风险事件）⚠️

| 枚举值 | 中文显示 | 触发 451 |
|---|---|---|
| cold_violence | 冷暴力 | **是** |
| physical_conflict | 肢体冲突 | **是** |
| economic_control | 经济控制 | **是** |
| threats | 威胁恐吓 | **是** |
| none | 以上都没有 | 否 |

> `none` 与其他值互斥，前端需做互斥处理

### UserPrimaryIntent（Q6 用户意图）

| 枚举值 | 中文显示 | 影响 |
|---|---|---|
| reconcile | 想挽回 | → 计划/改写 |
| understand | 想弄明白 | → 聊天/评估 |
| letgo | 想放下 | → 放下模式（P7） |
| unsure | 还不确定 | 中性流向 |

> ⚠️ 此字段**不参与评分**，仅影响 recommended_action 和页面跳转

### TimeSinceIncident（Q7 事件时间）

| 枚举值 | 中文显示 |
|---|---|
| within_24h | 24小时内 |
| 1d_to_7d | 1到7天 |
| 1w_to_4w | 1到4周 |
| over_1m | 一个多月了 |

### AssessmentLevel（评估等级）

| 枚举值 | 规则 | 颜色 |
|---|---|---|
| green | score ≥ 65 | 🟢 |
| yellow | 35 ≤ score < 65 | 🟡 |
| red | score < 35 或 OVERRIDE_RED | 🔴 |

### RecommendedAction（推荐行动）

| 枚举值 | 中文含义 | 触发条件 |
|---|---|---|
| stabilize_emotion | 先稳住情绪，暂时不要联系 | level=red/yellow + intent≠letgo |
| enter_no_contact | 进入冷静期，给彼此空间 | level=yellow |
| attempt_light_contact | 可以尝试轻量级沟通 | level=green + intent=reconcile |
| enter_letgo_mode | 先照顾自己，考虑放下 | level=red 或 intent=letgo |

### EmotionLabel（情绪标签）

| 枚举值 | 中文 | Emoji | 微干预类型 |
|---|---|---|---|
| anger | 愤怒 | 😤 | step_away |
| sadness | 悲伤 | 😢 | delay_send |
| guilt | 自责 | 😔 | delay_send |
| anxiety | 焦虑 | 😰 | breathe |
| fear | 恐惧 | 😨 | delay_send |
| calm | 平静 | 😌 | — |

### RewriteVersion（改写版本）

| 枚举值 | 中文名 | 前端标签色 |
|---|---|---|
| gentle | 温和版 | 蓝色 |
| direct | 直接版 | 红色 |
| brief | 简短版 | 绿色 |

### RiskLevel（风险等级）

| 枚举值 | 说明 | send_recommended |
|---|---|---|
| low | 低风险 | true（AI 判断）|
| medium | 中等风险 | true（AI 判断）|
| high | 高风险 | **强制 false** |

### PlanStage（计划阶段）

| 枚举值 | 中文名 | 阶段索引 |
|---|---|---|
| stabilize | 冷静期 | 0 |
| probe | 试探期 | 1 |
| communicate | 沟通期 | 2 |
| rebuild | 重建期 | 3 |

### ContactOutcome（联系结果）

| 枚举值 | 中文显示 |
|---|---|
| positive | 正向 |
| neutral | 中性 |
| negative | 负向 |
