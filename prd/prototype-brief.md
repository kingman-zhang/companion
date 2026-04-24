# RelationshipAI MVP 原型与开发交付简版

## 1. 这份文档的用途

这是一份给 UI 生成工具和后续 Codex 开发共同使用的收口版本。

目标有两个：

* 让 Pencil、Stitch 之类的工具生成同一套信息架构下的原型，而不是松散页面集合
* 让 Codex 后续开发时有稳定的 MVP 边界，不被产品愿景和 Phase 2 页面混淆

---

## 2. 首版开发 MVP 范围

首版必须开发：

* P0 首页 / 状态选择
* P1 问卷页 / 7题单步表单
* P2 评估结果页 / 红黄绿结果与下一步 CTA
* P3 情绪急救聊天页
* P4 消息改写页
* P8 安全拦截页
* 基础付费拦截组件

首版不开发：

* P5 挽回计划页
* P6 每日日志页
* P7 放下模式增强页
* 情绪趋势图
* 推送与自动提醒

说明：

* 原型阶段可以额外产出 P5/P6/P7 作为未来态概念页
* 但 Codex 开发第一版只对 MVP 页面负责

---

## 3. 产品定位

RelationshipAI 不是泛陪聊产品，也不是心理治疗产品。

它解决的是关系危机中的三个即时问题：

* 我现在情绪很乱，需要先被接住
* 我想知道这段关系值不值得继续投入
* 我怕自己发错消息，想先改写再决定

---

## 4. 核心用户

年龄 22-38 岁，正在经历：

* 刚分手
* 反复拉扯
* 怀疑出轨
* 婚姻冲突升级
* 想挽回但怕做错
* 想放下但不知怎么开始

用户状态特征：

* 高情绪波动
* 强烈需要即时反馈
* 更偏移动端使用
* 对隐私和安全敏感

---

## 5. 单一真相：问卷与评估模型

### 7题问卷字段

| 题号 | 字段 | 说明 |
| --- | --- | --- |
| Q1 | relationship_duration | 关系持续时长 |
| Q2 | who_initiated | 谁先提出分手或明显疏远 |
| Q3 | contact_status | 当前联系状态 |
| Q4 | infidelity_present | 是否存在出轨或疑似出轨 |
| Q5 | abuse_flags | 是否存在冷暴力、威胁、控制、肢体冲突等 |
| Q6 | user_primary_intent | 用户当前更想挽回、弄明白、放下，还是不确定 |
| Q7 | time_since_incident | 本次事件距离现在多久 |

### 评分因子

参与关系得分的只有 6 项：

* relationship_duration
* who_initiated
* contact_status
* infidelity_present
* abuse_flags
* time_since_incident

说明：

* `user_primary_intent` 不参与关系得分
* `user_primary_intent` 只用于结果页 CTA 和后续流向

### 风险分级

* Green：65-100，可谨慎尝试沟通
* Yellow：35-64，建议先稳定情绪，再谨慎试探
* Red：0-34，或命中严重安全项

### Override 规则

下列情况直接 Red，不继续常规评分：

* 家暴
* 明确暴力威胁
* 自伤/自杀相关风险
* 严重控制或跟踪风险

### 置信度

`confidence = 输入完整率 × 关键项明确度 × 枚举合法率`

原则：

* 置信度是规则结果，不由 LLM 决定
* 置信度低时结果页必须显示免责声明

---

## 6. MVP 主流程

```text
P0 首页
 -> P1 问卷
 -> P2 评估结果
 -> P3 情绪聊天
 -> P4 消息改写

任意页
 -> P8 安全拦截页
```

结果页分流原则：

* 主按钮永远是“先处理情绪”
* 次按钮根据 `level + user_primary_intent` 展示
* 首版开发不进入计划页和日志页

---

## 7. 页面清单与每页目标

### P0 首页

目标：让用户快速进入，不要被复杂选择阻挡。

必须包含：

* 情绪状态卡片入口
* 隐私承诺
* 明确、不过度夸张的文案

### P1 问卷页

目标：以低压方式收集结构化信息。

必须包含：

* 单题逐步流程
* 明确进度
* 前后切换
* 安全项命中后的中断逻辑

### P2 评估结果页

目标：给出可信但克制的判断，并引导下一步。

必须包含：

* 红黄绿状态
* 分数与解释
* 低置信度免责声明
* 主 CTA 和次 CTA

### P3 情绪聊天页

目标：先接住情绪，再提供谨慎建议。

必须包含：

* 对话流
* 当前情绪标签/强度
* 微干预卡片
* 安全拦截
* 对改写页的快捷入口

### P4 消息改写页

目标：帮助用户把冲动表达改成更稳妥的表达。

必须包含：

* 原文输入
* 至少 1 个免费版结果
* 风险等级
* 是否建议发送
* 复制操作
* 付费遮罩

### P8 安全拦截页

目标：停止交互，转向现实资源。

必须包含：

* 清晰风险提示
* 热线/资源卡
* 不可继续聊天或改写
* 冷却逻辑说明

---

## 8. 给 Pencil / Stitch 的设计要求

### 视觉方向

关键词：

* calm
* intimate
* trustworthy
* emotionally steady
* not clinical
* not playful

避免：

* 心理治疗 App 式医疗化界面
* 恋爱社交 App 式粉色甜腻视觉
* 过度科技感、紫色发光、赛博风

建议方向：

* 柔和中性色 + 单一强调色
* 卡片式结构
* 大留白
* 明确层级
* 在高情绪页面使用更稳定的视觉节奏

### 文案语气

* 共情，但不讨好
* 克制，不煽动
* 直接，不空泛
* 不承诺“帮你挽回成功”
* 不制造依赖感

---

## 9. 给 Pencil / Stitch 的原型 Prompt 模板

### 中文版

为一款“关系危机 AI 陪伴产品”设计一套移动端优先的产品原型。目标用户是 22-38 岁、正在经历分手、婚姻冲突、出轨怀疑等高情绪关系危机的人群。产品不是泛聊天，也不是心理治疗工具，而是帮助用户完成“情绪急救 -> 关系评估 -> 消息改写”的核心流程。

请设计以下页面：
1. 首页状态选择
2. 7题单步问卷
3. 红黄绿评估结果页
4. 情绪急救聊天页
5. 消息改写页
6. 安全拦截页

设计要求：
* 移动端优先，适配 iPhone 主流尺寸
* 风格 calm、trustworthy、emotionally steady
* 不要医疗化，不要社交化，不要赛博科技风
* 首页强调低门槛进入
* 评估页要突出结果等级、解释和下一步 CTA
* 聊天页要有情绪标签、微干预卡片和安全拦截态
* 改写页要有改写版本卡、风险标识、复制按钮和付费遮罩
* 安全页必须明显中断流程并展示求助资源

### 英文版

Design a mobile-first prototype for an AI relationship crisis companion product. The target users are adults aged 22-38 who are going through breakup pain, marriage conflict, suspected infidelity, or emotionally intense relationship situations. This is not a general chat app and not a therapy product. Its core flow is emotional first aid -> relationship assessment -> message rewrite.

Create these screens:
1. Home / state selection
2. 7-step questionnaire
3. Assessment result screen with red-yellow-green status
4. Emotional first-aid chat
5. Message rewrite
6. Safety interruption screen

Requirements:
* Mobile-first UI
* Calm, intimate, trustworthy, emotionally steady visual tone
* Avoid medical UI, dating app aesthetics, and flashy futuristic styles
* The home screen should feel low-friction and private
* The result screen should emphasize status, explanation, and next-step CTA
* The chat screen should include emotion tags, micro-intervention cards, and a safety lock state
* The rewrite screen should include rewrite cards, risk badges, copy action, and a paywall layer
* The safety screen must interrupt the flow and redirect the user to real-world support resources

---

## 10. 给 Codex 的开发输入

后续 Codex 开发第一版时，按下面的优先级拆：

1. 信息架构与路由
2. 问卷状态机
3. 评估规则引擎
4. 聊天和改写 UI
5. 安全拦截与降级策略
6. 付费拦截组件

开发原则：

* 规则决定分数、等级、置信度、推荐动作
* LLM 只负责解释、共情回复、改写候选
* 所有高风险场景优先中断，而不是继续生成内容
* 先把 MVP 跑通，再接计划/日志等 Phase 2
