// ============================================================
// RelationshipAI — 共享 TypeScript 类型定义
// 与 doc/api-schema.yaml 保持同步
// ============================================================

// ──────────────── Enums ────────────────

export type EntryState = 'just_dumped' | 'third_party' | 'near_breakup' | 'just_confused'

export type RelationshipDuration = 'less_than_6m' | '6m_to_2y' | '2y_to_5y' | 'over_5y'

export type WhoInitiated = 'me' | 'partner' | 'both' | 'unclear'

export type ContactStatus = 'no_contact' | 'occasional' | 'normal' | 'living_together'

export type AbuseFlag = 'cold_violence' | 'physical_conflict' | 'economic_control' | 'threats' | 'none'

export type UserPrimaryIntent = 'reconcile' | 'understand' | 'letgo' | 'unsure'

export type TimeSinceIncident = 'within_24h' | '1d_to_7d' | '1w_to_4w' | 'over_1m'

export type AssessmentLevel = 'red' | 'yellow' | 'green'

export type RecommendedAction =
  | 'stabilize_emotion'
  | 'enter_no_contact'
  | 'attempt_light_contact'
  | 'enter_letgo_mode'

export type EmotionLabel = 'anger' | 'sadness' | 'guilt' | 'anxiety' | 'fear' | 'calm'

export type RewriteVersion = 'gentle' | 'direct' | 'brief'

export type RiskLevel = 'low' | 'medium' | 'high'

export type PlanStage = 'stabilize' | 'probe' | 'communicate' | 'rebuild'

export type ContactOutcome = 'positive' | 'neutral' | 'negative'

export type MicroInterventionType = 'breathe' | 'step_away' | 'delay_send'

export type SafetyTriggerType = 'self_harm' | 'violence' | 'abuse_flags'

// ──────────────── Request Bodies ────────────────

export interface QuestionnaireInput {
  session_id?: string
  entry_state?: EntryState
  relationship_duration: RelationshipDuration
  who_initiated: WhoInitiated
  contact_status: ContactStatus
  infidelity_present: boolean
  abuse_flags: AbuseFlag[]
  user_primary_intent: UserPrimaryIntent
  time_since_incident: TimeSinceIncident
}

export interface ChatRequest {
  session_id: string
  content: string
}

export interface RewriteRequest {
  session_id?: string
  original_message: string
}

export interface LogRequest {
  date: string
  emotion_score: number
  emotion_label: EmotionLabel
  contacted_ex: boolean
  contact_outcome?: ContactOutcome
  notes?: string
}

// ──────────────── Response Bodies ────────────────

export interface AssessmentRuleFactors {
  relationship_duration: number
  contact_status: number
  who_initiated: number
  infidelity_present: number
  abuse_flags: number
  time_since_incident: number
}

export interface AssessmentResult {
  assessment_id: string
  score: number
  level: AssessmentLevel
  confidence: number
  rule_factors: AssessmentRuleFactors
  llm_reason: string
  recommended_action: RecommendedAction
  created_at: string
}

export interface MicroIntervention {
  type: MicroInterventionType
  title: string
  body: string
  action_label: string
  secondary_action_label?: string | null
}

export interface ChatResponse {
  message_id: string
  session_id: string
  role: 'assistant'
  content: string
  emotion_label: EmotionLabel
  emotion_intensity: number
  safety_flag: boolean
  micro_intervention: MicroIntervention | null
  created_at: string
}

export interface RewriteVariant {
  version: RewriteVersion
  content: string
  risk_level: RiskLevel
  risk_reason: string
  send_recommended: boolean
  confidence: number
}

export interface RewriteResponse {
  rewrite_id: string
  variants: RewriteVariant[]
  created_at: string
}

// ──────────────── Error Types ────────────────

export interface ErrorResponse {
  code: string
  message: string
  detail: Record<string, unknown> | null
}

export interface SafetyErrorResponse {
  code: 'SAFETY_BLOCKED'
  message: string
  trigger_type: SafetyTriggerType
  session_cooldown_until: string | null
}

// ──────────────── Mock Result ────────────────

export interface MockResult<T> {
  statusCode: number
  body: T | ErrorResponse | SafetyErrorResponse
}
