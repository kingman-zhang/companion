// ============================================================
// RelationshipAI — Mock: POST /api/v1/assessment
//
// 用法（开发时）：
//   import { mockAssessment } from '@/mock/assessment'
//   const result = mockAssessment(requestBody, 'success_yellow')
//   // result.statusCode === 200, result.body === AssessmentResult
// ============================================================

import type {
  QuestionnaireInput,
  AssessmentResult,
  ErrorResponse,
  SafetyErrorResponse,
  MockResult,
} from './types'

type AssessmentScenario =
  | 'success_green'
  | 'success_yellow'
  | 'success_red'
  | 'safety_blocked'
  | 'service_unavailable'
  | 'invalid_enum'

// ──────────────────────────────────────────────
// 主函数：根据请求体自动选择场景，也可手动指定场景
// ──────────────────────────────────────────────
export function mockAssessment(
  body: Partial<QuestionnaireInput>,
  forceScenario?: AssessmentScenario
): MockResult<AssessmentResult> {
  if (forceScenario) {
    return SCENARIOS[forceScenario]
  }

  // 自动模拟：abuse_flags 非 none → 451
  if (body.abuse_flags?.some((f) => f !== 'none')) {
    return SCENARIOS.safety_blocked
  }

  // 根据简单规则自动返回不同等级（开发用快速预览）
  if (
    body.infidelity_present === true ||
    body.contact_status === 'no_contact'
  ) {
    return SCENARIOS.success_red
  }

  if (body.contact_status === 'living_together' || body.relationship_duration === 'over_5y') {
    return SCENARIOS.success_green
  }

  return SCENARIOS.success_yellow
}

// ──────────────────────────────────────────────
// 场景集合（可单独导出供单元测试使用）
// ──────────────────────────────────────────────
const NOW = new Date().toISOString()

export const SCENARIOS: Record<AssessmentScenario, MockResult<AssessmentResult>> = {

  // ✅ 绿灯：关系有修复空间
  success_green: {
    statusCode: 200,
    body: {
      assessment_id: 'asmt_mock_green_001',
      score: 72.5,
      level: 'green',
      confidence: 0.88,
      rule_factors: {
        relationship_duration: 80,  // over_5y
        contact_status: 75,         // normal
        who_initiated: 70,          // me（我主动，对方未明确拒绝）
        infidelity_present: 90,     // false
        abuse_flags: 100,           // none
        time_since_incident: 60,    // 1w_to_4w
      },
      llm_reason: '你们交往时间长，目前还有联系，对方也没有明确拒绝，关系修复存在一定空间，但需要冷静处理。',
      recommended_action: 'attempt_light_contact',
      created_at: NOW,
    } satisfies AssessmentResult,
  },

  // ⚠️ 黄灯：需谨慎，建议先稳定情绪
  success_yellow: {
    statusCode: 200,
    body: {
      assessment_id: 'asmt_mock_yellow_001',
      score: 48.0,
      level: 'yellow',
      confidence: 0.75,
      rule_factors: {
        relationship_duration: 60,  // 6m_to_2y
        contact_status: 40,         // occasional
        who_initiated: 30,          // partner（对方主动疏远）
        infidelity_present: 90,     // false
        abuse_flags: 100,           // none
        time_since_incident: 60,    // 1d_to_7d
      },
      llm_reason: '对方主动疏远，联系减少，情绪还在高位。建议先给自己一段冷静时间，不要急于联系。',
      recommended_action: 'stabilize_emotion',
      created_at: NOW,
    } satisfies AssessmentResult,
  },

  // 🔴 红灯：高风险，建议优先照顾自己
  success_red: {
    statusCode: 200,
    body: {
      assessment_id: 'asmt_mock_red_001',
      score: 22.0,
      level: 'red',
      confidence: 0.92,
      rule_factors: {
        relationship_duration: 50,  // 6m_to_2y
        contact_status: 20,         // no_contact
        who_initiated: 30,          // partner
        infidelity_present: 10,     // true（出轨）
        abuse_flags: 100,           // none（未触发安全，但有出轨）
        time_since_incident: 35,    // over_1m
      },
      llm_reason: '目前对方完全无联系且存在出轨情况，现阶段挽回可能性较低，优先保护自己的情绪和尊严更重要。',
      recommended_action: 'enter_letgo_mode',
      created_at: NOW,
    } satisfies AssessmentResult,
  },

  // 🚨 451 安全拦截：abuse_flags 非 none
  safety_blocked: {
    statusCode: 451,
    body: {
      code: 'SAFETY_BLOCKED',
      message: '检测到安全风险，已中断当前操作',
      trigger_type: 'abuse_flags',
      session_cooldown_until: null,
    } satisfies SafetyErrorResponse,
  },

  // 💥 503 AI 服务不可用
  service_unavailable: {
    statusCode: 503,
    body: {
      code: 'AI_SERVICE_UNAVAILABLE',
      message: 'AI助手当前繁忙，请稍后重试',
      detail: null,
    } satisfies ErrorResponse,
  },

  // ❌ 422 枚举值非法
  invalid_enum: {
    statusCode: 422,
    body: {
      code: 'INVALID_ENUM_VALUE',
      message: '字段 who_initiated 值非法，合法值: me, partner, both, unclear',
      detail: {
        field: 'who_initiated',
        allowed: ['me', 'partner', 'both', 'unclear'],
      },
    } satisfies ErrorResponse,
  },
}
