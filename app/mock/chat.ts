// ============================================================
// RelationshipAI — Mock: POST /api/v1/chat
//
// 包含 6 种情绪标签的回复示例，以及微干预卡片示例。
// ============================================================

import type {
  ChatRequest,
  ChatResponse,
  EmotionLabel,
  ErrorResponse,
  SafetyErrorResponse,
  MicroIntervention,
  MockResult,
} from './types'

type ChatScenario =
  | 'emotion_sadness'
  | 'emotion_anger'       // 触发 step_away 微干预
  | 'emotion_anxiety'     // 触发 breathe 微干预
  | 'emotion_guilt'       // 触发 delay_send 微干预
  | 'emotion_fear'
  | 'emotion_calm'
  | 'safety_blocked'
  | 'rate_limited'
  | 'service_unavailable'

let msgCounter = 0

// ──────────────────────────────────────────────
// 主函数
// ──────────────────────────────────────────────
export function mockChat(
  body: Partial<ChatRequest>,
  forceScenario?: ChatScenario
): MockResult<ChatResponse> {
  if (forceScenario) {
    return SCENARIOS[forceScenario]
  }

  // 关键词检测模拟
  const content = body.content ?? ''
  const SAFETY_KEYWORDS = ['自杀', '死了', '杀', '家暴', '伤害自己', '消失']
  if (SAFETY_KEYWORDS.some((kw) => content.includes(kw))) {
    return SCENARIOS.safety_blocked
  }

  // 简单情绪关键词路由（开发预览用）
  if (content.includes('愤怒') || content.includes('生气') || content.includes('气死')) {
    return SCENARIOS.emotion_anger
  }
  if (content.includes('焦虑') || content.includes('担心') || content.includes('怎么办')) {
    return SCENARIOS.emotion_anxiety
  }
  if (content.includes('后悔') || content.includes('都是我的错') || content.includes('自责')) {
    return SCENARIOS.emotion_guilt
  }
  if (content.includes('怕') || content.includes('害怕') || content.includes('恐惧')) {
    return SCENARIOS.emotion_fear
  }
  if (content.includes('平静') || content.includes('好多了') || content.includes('没事')) {
    return SCENARIOS.emotion_calm
  }

  return SCENARIOS.emotion_sadness
}

// ──────────────────────────────────────────────
// 微干预卡片数据
// ──────────────────────────────────────────────
const MICRO_BREATHE: MicroIntervention = {
  type: 'breathe',
  title: '先深呼吸一下',
  body: '4秒吸气 → 4秒屏息 → 4秒呼气，重复3次。让身体先平静下来。',
  action_label: '好的，我试试',
  secondary_action_label: null,
}

const MICRO_STEP_AWAY: MicroIntervention = {
  type: 'step_away',
  title: '先离开这里一会儿',
  body: '现在不是发消息的最好时机，先放下手机，出去走走或者喝杯水。',
  action_label: '好，我先缓缓',
  secondary_action_label: null,
}

const MICRO_DELAY_SEND: MicroIntervention = {
  type: 'delay_send',
  title: '先别发那条消息',
  body: '等 10 分钟再决定，好吗？情绪平稳后再说往往更有效，你会感谢现在的自己。',
  action_label: '好的，我等等',
  secondary_action_label: '帮我改写它',
}

// ──────────────────────────────────────────────
// 辅助：构造回复
// ──────────────────────────────────────────────
function makeResponse(
  sessionId: string,
  content: string,
  emotionLabel: EmotionLabel,
  intensity: number,
  microIntervention: MicroIntervention | null = null
): MockResult<ChatResponse> {
  return {
    statusCode: 200,
    body: {
      message_id: `msg_mock_${++msgCounter}`,
      session_id: sessionId,
      role: 'assistant',
      content,
      emotion_label: emotionLabel,
      emotion_intensity: intensity,
      safety_flag: false,
      micro_intervention: intensity >= 8 ? microIntervention : null,
      created_at: new Date().toISOString(),
    } satisfies ChatResponse,
  }
}

// ──────────────────────────────────────────────
// 场景集合
// ──────────────────────────────────────────────
export const SCENARIOS: Record<ChatScenario, MockResult<ChatResponse | ErrorResponse | SafetyErrorResponse>> = {

  // 😢 悲伤 — intensity 中等，无微干预
  emotion_sadness: makeResponse(
    'sess_mock',
    '我听到你了，这种感觉真的很难受。分手带来的痛是真实的，你不需要假装没事。能告诉我，现在最让你难过的是什么吗？',
    'sadness',
    6
  ),

  // 😤 愤怒 — intensity 高，触发 step_away 微干预
  emotion_anger: makeResponse(
    'sess_mock',
    '你的愤怒完全可以理解，这种背叛感太真实了。先把手机放下几分钟，我们等一下再聊，好吗？',
    'anger',
    9,
    MICRO_STEP_AWAY
  ),

  // 😰 焦虑 — intensity 高，触发 breathe 微干预
  emotion_anxiety: makeResponse(
    'sess_mock',
    '感受到你现在很焦虑，思绪乱成一团。先跟我做一个简单的呼吸练习，帮你把节奏慢下来。',
    'anxiety',
    8,
    MICRO_BREATHE
  ),

  // 😔 自责 — intensity 高，触发 delay_send 微干预
  emotion_guilt: makeResponse(
    'sess_mock',
    '你现在承担了太多责任。感情的问题从来不是一个人的错，你愿意反思是好的，但不要把所有事都压在自己身上。',
    'guilt',
    8,
    MICRO_DELAY_SEND
  ),

  // 😨 恐惧 — intensity 中等
  emotion_fear: makeResponse(
    'sess_mock',
    '这种不确定感和恐惧很正常，特别是在关系刚刚出现危机的时候。你害怕的具体是失去他/她，还是害怕未来一个人？',
    'fear',
    5
  ),

  // 😌 平静 — intensity 低
  emotion_calm: makeResponse(
    'sess_mock',
    '听起来你已经稍微平静一些了，这很好。在比较冷静的时候，我们可以更清楚地看这件事。你现在最想理清楚的是哪个方面？',
    'calm',
    2
  ),

  // 🚨 451 安全拦截
  safety_blocked: {
    statusCode: 451,
    body: {
      code: 'SAFETY_BLOCKED',
      message: '检测到安全风险，已中断本次聊天',
      trigger_type: 'self_harm',
      session_cooldown_until: null,
    } satisfies SafetyErrorResponse,
  },

  // ⏳ 429 超出免费轮次
  rate_limited: {
    statusCode: 429,
    body: {
      code: 'FREE_TIER_LIMIT_REACHED',
      message: '已达免费聊天上限（10轮），升级会员继续倾诉',
      detail: {
        limit_type: 'chat_rounds',
        limit_value: 10,
      },
    } satisfies ErrorResponse,
  },

  // 💥 503 AI 服务不可用
  service_unavailable: {
    statusCode: 503,
    body: {
      code: 'AI_SERVICE_UNAVAILABLE',
      message: 'AI助手繁忙，请稍后再试',
      detail: null,
    } satisfies ErrorResponse,
  },
}
