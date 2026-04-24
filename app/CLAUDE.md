# CLAUDE.md · 前端开发上下文（伴听 / RelationshipAI）

> 这个文件是给前端 Claude Code 读的项目上下文。
> 每次在此目录启动 Claude Code，它会自动读取本文件。

---

## 项目简介

**RelationshipAI（伴听）** 是一款面向 22-38 岁用户的 AI 情感陪伴 App。
核心流程：情绪状态选择 → 7 题关系评估 → 三维度结果页 → 情绪急救聊天 → 消息改写。

---

## 后端信息

| 项目 | 值 |
|------|-----|
| 本地地址 | `http://localhost:8080` |
| 接口文档 | `../doc/api-schema.yaml`（OpenAPI 3.0）|
| 响应格式 | 统一 IResult 包装（见下方）|
| 字段命名 | **snake_case**（请求体 + 响应体均为下划线）|
| 枚举命名 | **大写蛇形**（如 `LESS_THAN_3M`、`GREEN`）|
| 时间格式 | `"yyyy-MM-dd HH:mm:ss"`（非 ISO 8601）|

### 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1745241000000
}
```

- `code=200` → 成功，读 `data`
- `code!=200` → 业务错误，读 `message` 展示提示
- 只有 HTTP 451（安全拦截）和 HTTP 401（未授权）时 HTTP 状态码非 200

---

## MVP 页面与路由

| 页面 | 路由 | 对应接口 |
|------|------|----------|
| P0 首页 / 状态选择 | `/` | 无接口，entry_state 传后续请求 |
| P1 问卷页（7题单步） | `/questionnaire` | POST /api/v1/assessment |
| P2 评估结果页 | `/assessment/result` | 使用 P1 返回的 data |
| P3 情绪急救聊天页 | `/chat` | POST /api/v1/chat/session + POST /api/v1/chat |
| P4 消息改写页 | `/rewrite` | POST /api/v1/rewrite |
| P8 安全拦截页 | `/safety` | 触发条件：HTTP 451 |

**第一版不开发**：P5 挽回计划、P6 日志、P7 放下模式增强页。

---

## API 接口速查

### 1. POST /api/v1/assessment（提交问卷）

**请求体：**

```json
{
  "session_id": "可选",
  "entry_state": "BREAKDOWN",
  "relationship_duration": "SIX_MONTHS_TO_2Y",
  "breakup_method": "DURING_ARGUMENT",
  "current_emotion": "SAD",
  "communication_quality": "SURFACE_LEVEL",
  "conflict_style": "AVOID_THEN_IGNORE",
  "partner_love_perception": "UNSURE_CHANGED",
  "user_primary_intent": "RECONCILE"
}
```

**响应 data 结构：**

```json
{
  "assessment_id": "1234567890",
  "score": 63,
  "level": "YELLOW",
  "confidence": 1.0,
  "emotional_connection_score": 68,
  "communication_score": 62,
  "conflict_score": 58,
  "core_insight": "你们缺的不是感情，是喘息空间。",
  "llm_reason": "关系处于敏感期，有些因素对挽回有利，但也存在障碍。先稳定自己的情绪。",
  "recommended_action": "COOL_DOWN",
  "user_primary_intent": "RECONCILE",
  "created_at": "2026-04-21 10:30:00"
}
```

### 2. GET /api/v1/assessment/{assessmentId}（查询评估结果）

同上响应结构，用于页面刷新/分享回显。

### 3. POST /api/v1/chat/session（创建会话）

无请求体，响应 data：

```json
{ "session_id": "1234567890123456" }
```

进入聊天页面时先调此接口，拿到 session_id 后再发消息。

### 4. POST /api/v1/chat（发送消息）

**请求体：**

```json
{
  "session_id": "1234567890123456",
  "content": "我真的很难受，不知道怎么办"
}
```

**响应 data 结构：**

```json
{
  "message_id": "1234567890123457",
  "session_id": "1234567890123456",
  "role": "assistant",
  "content": "我听到你了，这种感觉真的很难受。先深吸一口气，我陪着你。",
  "emotion_label": "SADNESS",
  "emotion_intensity": 5,
  "safety_flag": false,
  "micro_intervention": null,
  "created_at": "2026-04-21 10:32:00"
}
```

`emotion_intensity ≥ 8` 时 `micro_intervention` 不为 null，需渲染微干预卡片。

免费层限制：10 轮，超出时 `code=429001`，`data=null`，展示付费引导。

### 5. POST /api/v1/rewrite（消息改写）

**请求体：**

```json
{
  "session_id": "可选",
  "original_message": "你为什么要这样对我，我真的好失望"
}
```

**响应 data 结构：**

```json
{
  "rewrite_id": "1234567890123459",
  "variants": [
    {
      "version": "gentle",
      "content": "我最近感觉我们之间出现了一些距离，我很在乎你，想和你好好谈谈。",
      "risk_level": "low",
      "risk_reason": "表达较为克制，风险较低",
      "send_recommended": true,
      "confidence": 0.85
    },
    { "version": "direct", ... },
    { "version": "brief", ... }
  ],
  "created_at": "2026-04-21 10:35:00"
}
```

**付费规则：** 第 2（direct）和第 3（brief）个变体需要毛玻璃遮罩，点击触发付费引导。
免费层限制：每日 1 次（`code=429001` 时展示付费引导）。

---

## 问卷选项映射表（UI 文案 → API 枚举值）

### entry_state（P0 首页状态卡）

| UI 显示 | API 值 |
|---------|--------|
| 情绪崩了，什么都不想管 | `BREAKDOWN` |
| 很想联系TA，但不知道说什么 | `WANT_CONTACT` |
| 脑子里全是这件事，停不下来 | `RUMINATING` |
| 我想搞清楚，这段关系还有没有可能 | `WANT_CLARITY` |

### Q1: relationship_duration（你们在一起多久了）

| UI 显示 | API 值 |
|---------|--------|
| 不到3个月 | `LESS_THAN_3M` |
| 半年到2年 | `SIX_MONTHS_TO_2Y` |
| 2到5年 | `TWO_TO_5Y` |
| 5年以上 | `MORE_THAN_5Y` |

### Q2: breakup_method（TA 是怎么提出来的）

| UI 显示 | API 值 |
|---------|--------|
| 吵架时说的，情绪激动 | `DURING_ARGUMENT` |
| 当面平静地提出来 | `FACE_TO_FACE_CALM` |
| 发消息/打电话提的 | `MESSAGE` |
| 突然消失，没有明确提出 | `GHOSTED` |

### Q3: current_emotion（现在你最强烈的感受是）

| UI 显示 | API 值 |
|---------|--------|
| 震惊，感觉像在做梦 | `SHOCKED` |
| 愤怒，觉得不公平 | `ANGRY` |
| 难过，很想念TA | `SAD` |
| 想搞清楚，冷静理性 | `DETERMINED` |

### Q4: communication_quality（分手前3个月，你们的沟通怎样）

| UI 显示 | API 值 |
|---------|--------|
| 日常沟通顺畅，偶有摩擦 | `GOOD_DAILY` |
| 表面平静，但很少深聊 | `SURFACE_LEVEL` |
| 频繁争吵或冷战 | `FREQUENT_CONFLICT` |
| TA 开始冷漠、回避我 | `PARTNER_COLD` |

### Q5: conflict_style（你们吵架时，通常会）

| UI 显示 | API 值 |
|---------|--------|
| 冷静一下，再沟通解决 | `RESOLVE_AFTER_CALM` |
| 先冷战，慢慢不了了之 | `AVOID_THEN_IGNORE` |
| 一方主动道歉，另一方接受 | `ONE_SIDED_APOLOGY` |
| 越吵越激烈，翻旧账 | `ESCALATE_DIG_UP_PAST` |

### Q6: partner_love_perception（你觉得 TA 还爱你吗）

| UI 显示 | API 值 |
|---------|--------|
| 爱，但被压力/现实/家人影响 | `YES_EXTERNAL_PRESSURE` |
| 说不准，TA 好像变了 | `UNSURE_CHANGED` |
| 可能不爱了，但我放不下 | `MAYBE_NOT_CANT_LET_GO` |
| 不爱了，只是我还没接受 | `NO_JUST_CANT_MOVE_ON` |

### Q7: user_primary_intent（现在你最想要什么）

| UI 显示 | API 值 |
|---------|--------|
| 想挽回，重新在一起 | `RECONCILE` |
| 先处理好自己的情绪 | `PROCESS_EMOTION_FIRST` |
| 想学会放下 | `LEARN_GOODBYE` |
| 还没想好，先聊聊 | `CHAT_FIRST` |

---

## 评估结果页（P2）字段用法

| 字段 | 用途 |
|------|------|
| `level` | 页面主题色（GREEN/YELLOW/RED）、状态标签 |
| `score` | 大数字展示（如"63分"）|
| `emotional_connection_score` | 情感联结进度条（0-100）|
| `communication_score` | 沟通质量进度条（0-100）|
| `conflict_score` | 冲突处理进度条（0-100）|
| `core_insight` | 引用块文字（一句话洞察，加引号展示）|
| `llm_reason` | 洞察说明文字（正文段落，≤80字）|
| `recommended_action` | 主 CTA 按钮文案和跳转目标 |
| `user_primary_intent` | 次 CTA 按钮逻辑 |
| `confidence` | < 0.4 时展示"仅供参考"免责声明 |

### level 对应颜色

| level | 主色 | 背景色 |
|-------|------|--------|
| GREEN | #3D7A5A | #EDF6F0 |
| YELLOW | #A07830 | #FDF6E3 |
| RED | #9B4040 | #FBF0F0 |

### recommended_action 对应 CTA

| 值 | 主按钮文案 | 跳转 |
|----|-----------|------|
| CONSIDER_RECONCILE | 查看挽回建议 | /plan（Phase 2）|
| COOL_DOWN | 先聊聊，稳定情绪 | /chat |
| LET_GO | 和 AI 聊聊怎么放下 | /chat |
| SEEK_PROFESSIONAL_HELP | 和 AI 聊聊，获得支持 | /chat |

---

## 微干预卡片（P3 聊天页）

`emotion_intensity ≥ 8` 时弹出，覆盖输入框上方：

| type | title | 触发条件 | secondary_action_label |
|------|-------|---------|------------------------|
| breathe | 先深呼吸一下 | emotion_label=ANXIETY | 无 |
| step_away | 先离开一会儿 | emotion_label=ANGER | 无 |
| delay_send | 先别发，改一改？ | 其他高强度情绪 | "帮我改写它"（跳 /rewrite）|

---

## 错误码处理

| business code | 含义 | 前端处理 |
|---------------|------|---------|
| 200 | 成功 | 正常渲染 |
| 400xxx | 参数错误 | Toast 提示 message |
| 404001 | 资源不存在 | 提示"评估结果不存在" |
| 429001 | 超出免费限制 | 展示付费引导（PaywallBanner）|
| 500xxx | 服务器错误 | Toast "服务异常，请稍后重试" |
| HTTP 451 | 安全拦截 | 跳转 /safety 全屏安全页 |

---

## 设计规范速查（详见 ../prd/ui-spec.md）

```
背景色：#EDE8DC（首页）/ #F5F3F0（其他页）
强调色：#4A7B7B（墨绿，主按钮/边框/激活态）
Logo 绿：#2F5E38
字体：PingFang SC（中文）+ DM Sans（数字/英文）
卡片圆角：24rpx（大卡片）/ 20rpx（场景卡）/ 999rpx（胶囊按钮）
页面 padding：40rpx（左右）
按钮高度：60rpx，圆角 999rpx（胶囊）
```

### 微信小程序字体规范（rpx，基准 750rpx = 375px）

| 层级 | class 约定 | font-size | font-weight | 用途 |
|------|-----------|-----------|-------------|------|
| Display | `.text-display` | 44rpx | 700 | 页面大标题（如问候语）|
| H2 | `.text-h2` | 32rpx | 600 | 品牌名、卡片大标题 |
| Body | `.text-body` | 28rpx | 400 | 正文、卡片标题 |
| Secondary | `.text-secondary` | 24rpx | 400 | 辅助说明、输入框文字、占位符 |
| Caption | `.text-caption` | 20rpx | 400/500 | 标签、tab 文字、免责声明 |

### 颜色速查

| 用途 | 色值 |
|------|------|
| 主文字 | `#1C1C1E` |
| 次要文字 | `#A0A0A0` |
| 三级文字 | `#C0BAB0` |
| 强调绿（按钮/边框）| `#4A7B7B` |
| 强调绿文字（白底上）| `#FFFFFF`（配 #4A7B7B 背景）|
| 禁用按钮背景 | `#E8E5DF` |
| 禁用按钮文字 | `#B8B4AE` |
| 卡片背景 | `#FFFFFF` |
| 输入卡片描边 | `#4A7B7B`（2rpx solid）|
| Tab 激活色 | `#2F5E38` |
| Tab 非激活色 | `#C0B8AE` |

---

## 复用组件清单（优先建立）

| 组件名 | 复用页面 | 说明 |
|--------|---------|------|
| `StateLabel` | P2、P3、P4 | 红黄绿状态徽章 |
| `SafetyInterstitial` | P3、P4 | 全屏安全覆盖层（HTTP 451 时触发）|
| `MicroInterventionCard` | P3 | 微干预卡片（emotion_intensity ≥ 8 触发）|
| `RewriteVariantCard` | P4 | 改写版本卡片（含毛玻璃遮罩逻辑）|
| `PaywallBanner` | P3、P4 | 付费拦截横条（code=429001 时展示）|
| `ScoreDimension` | P2 | 三维度进度条（情感联结/沟通质量/冲突处理）|

---

## 注意事项

1. **枚举值大小写**：问卷枚举（RelationshipDuration 等）为大写，改写枚举（RewriteVersion、RiskLevel）为小写，注意区分。
2. **session_id 来源**：进入聊天页先调 `POST /chat/session` 拿到 session_id，再发消息。不要复用评估的 session_id（那个是可选的用户传入值）。
3. **时间格式**：后端返回 `"2026-04-21 10:30:00"` 格式，非 ISO 8601，前端 `new Date()` 解析前需注意兼容处理。
4. **HTTP 451**：安全拦截响应不走 IResult 包装，直接是 `{code: "SAFETY_BLOCKED", message, trigger_type, session_cooldown_until}` 结构。
5. **改写付费遮罩**：variants 数组第 2、3 个（index 1、2）需毛玻璃遮罩，index 0（gentle）正常展示。
