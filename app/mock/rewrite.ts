// ============================================================
// RelationshipAI — Mock: POST /api/v1/rewrite
//
// 包含 3 种 variant（gentle/direct/brief）的完整示例。
// 包含针对不同原始消息风格的不同改写结果示例。
// ============================================================

import type {
  RewriteRequest,
  RewriteResponse,
  RewriteVariant,
  ErrorResponse,
  SafetyErrorResponse,
  MockResult,
} from './types'

type RewriteScenario =
  | 'success_low_risk'       // 低风险输入，3 版本均建议发送
  | 'success_medium_risk'    // 中风险输入，direct/brief 不建议
  | 'success_high_risk'      // 高风险输入，gentle 降级仍建议，其余不建议
  | 'safety_blocked'
  | 'rate_limited'
  | 'service_unavailable'
  | 'input_too_short'

let rewriteCounter = 0

// ──────────────────────────────────────────────
// 主函数
// ──────────────────────────────────────────────
export function mockRewrite(
  body: Partial<RewriteRequest>,
  forceScenario?: RewriteScenario
): MockResult<RewriteResponse> {
  if (forceScenario) {
    return SCENARIOS[forceScenario] as MockResult<RewriteResponse>
  }

  const msg = body.original_message ?? ''

  // 安全关键词检测
  const SAFETY_KEYWORDS = ['自杀', '去死', '杀了', '家暴']
  if (SAFETY_KEYWORDS.some((kw) => msg.includes(kw))) {
    return SCENARIOS.safety_blocked as MockResult<RewriteResponse>
  }

  // 输入长度检测
  if (msg.length < 10) {
    return SCENARIOS.input_too_short as MockResult<RewriteResponse>
  }

  // 简单情绪检测：含攻击性词汇 → 中/高风险
  const HIGH_RISK_KEYWORDS = ['恨你', '让你后悔', '报复', '不放过你']
  const MEDIUM_RISK_KEYWORDS = ['你为什么', '你怎么能', '你不在乎', '失望']

  if (HIGH_RISK_KEYWORDS.some((kw) => msg.includes(kw))) {
    return SCENARIOS.success_high_risk
  }
  if (MEDIUM_RISK_KEYWORDS.some((kw) => msg.includes(kw))) {
    return SCENARIOS.success_medium_risk
  }

  return SCENARIOS.success_low_risk
}

// ──────────────────────────────────────────────
// 辅助：构造改写响应
// ──────────────────────────────────────────────
function makeRewriteResponse(variants: RewriteVariant[]): MockResult<RewriteResponse> {
  return {
    statusCode: 200,
    body: {
      rewrite_id: `rw_mock_${++rewriteCounter}`,
      variants,
      created_at: new Date().toISOString(),
    } satisfies RewriteResponse,
  }
}

// ──────────────────────────────────────────────
// 场景集合
// ──────────────────────────────────────────────
export const SCENARIOS: Record<RewriteScenario, MockResult<RewriteResponse | ErrorResponse | SafetyErrorResponse>> = {

  // ✅ 低风险：原始消息较理性，三版本均建议发送
  // 模拟原始消息："最近我们的状态让我很担心，我想找个时间认真聊聊"
  success_low_risk: makeRewriteResponse([
    {
      version: 'gentle',
      content: '最近我们之间的状态让我有些担心，我很在乎我们的关系。如果你愿意的话，想找个时间好好聊聊，你方便吗？',
      risk_level: 'low',
      risk_reason: '表达温和，无攻击性',
      send_recommended: true,
      confidence: 0.92,
    },
    {
      version: 'direct',
      content: '我注意到我们最近的状态不太对，我想和你认真谈谈，这对我来说很重要。',
      risk_level: 'low',
      risk_reason: '直接表达需求，无负面情绪',
      send_recommended: true,
      confidence: 0.88,
    },
    {
      version: 'brief',
      content: '我们能找个时间聊聊吗？有些话我想说清楚。',
      risk_level: 'low',
      risk_reason: '简洁无压力',
      send_recommended: true,
      confidence: 0.85,
    },
  ]),

  // ⚠️ 中风险：原始消息含失望情绪，部分版本不建议发送
  // 模拟原始消息："你为什么要这样对我，我真的好失望"
  success_medium_risk: makeRewriteResponse([
    {
      version: 'gentle',
      content: '我最近心里有些难受，感觉我们之间出现了一些距离。我很在乎你，想和你谈谈，可以吗？',
      risk_level: 'low',
      risk_reason: '情绪已转化，表达积极',
      send_recommended: true,
      confidence: 0.86,
    },
    {
      version: 'direct',
      content: '你最近的一些做法让我感到失望，我希望能直接和你说清楚我的感受。',
      risk_level: 'medium',
      risk_reason: '含失望措辞，可能引起防御',
      send_recommended: false,
      confidence: 0.74,
    },
    {
      version: 'brief',
      content: '我有些失望，想和你聊聊。',
      risk_level: 'medium',
      risk_reason: '直接表达负面情绪，风险中等',
      send_recommended: false,
      confidence: 0.68,
    },
  ]),

  // 🔴 高风险：原始消息含攻击性措辞，即使改写后 direct/brief 也不建议
  // 模拟原始消息："我恨你，你会让我后悔的，我不会放过你"
  success_high_risk: makeRewriteResponse([
    {
      version: 'gentle',
      content: '现在我的情绪很激动，我需要一点时间冷静。等我平静下来，希望我们能好好谈谈。',
      risk_level: 'low',
      risk_reason: '原文情绪已完全转换，建议代替原文',
      send_recommended: true,
      confidence: 0.78,
    },
    {
      version: 'direct',
      content: '你的行为让我非常愤怒，我需要你认真对待这件事。',
      risk_level: 'high',
      risk_reason: '仍含指责，对方可能产生对抗',
      send_recommended: false,
      confidence: 0.55,
    },
    {
      version: 'brief',
      content: '我现在很愤怒，不适合沟通，等我冷静再说。',
      risk_level: 'medium',
      risk_reason: '情绪较强，建议等情绪平复后再发',
      send_recommended: false,
      confidence: 0.61,
    },
  ]),

  // 🚨 451 安全拦截
  safety_blocked: {
    statusCode: 451,
    body: {
      code: 'SAFETY_BLOCKED',
      message: '检测到敏感信息，已中断操作',
      trigger_type: 'violence',
      session_cooldown_until: null,
    } satisfies SafetyErrorResponse,
  },

  // ⏳ 429 超出每日改写限制
  rate_limited: {
    statusCode: 429,
    body: {
      code: 'FREE_TIER_LIMIT_REACHED',
      message: '已达今日免费改写上限（1次/天），升级会员解锁更多',
      detail: {
        limit_type: 'daily_rewrite',
        limit_value: 1,
        reset_at: new Date(new Date().setHours(24, 0, 0, 0)).toISOString(),
      },
    } satisfies ErrorResponse,
  },

  // 💥 503 AI 服务不可用
  service_unavailable: {
    statusCode: 503,
    body: {
      code: 'AI_SERVICE_UNAVAILABLE',
      message: '改写功能暂时不可用，请稍后再试',
      detail: null,
    } satisfies ErrorResponse,
  },

  // ❌ 400 输入太短
  input_too_short: {
    statusCode: 400,
    body: {
      code: 'INPUT_TOO_SHORT',
      message: '输入内容太短，至少需要10个字',
      detail: { min_length: 10 },
    } satisfies ErrorResponse,
  },
}
