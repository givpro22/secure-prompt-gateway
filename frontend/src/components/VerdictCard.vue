<script setup>
import { computed, ref } from 'vue'
import LlmPicker from './LlmPicker.vue'
import MaskedText from './MaskedText.vue'
import StatusBadge from './StatusBadge.vue'
import { ACTION_TERMS, CATEGORY_TERMS, OBLIGATION_TERMS, term } from '../lib/terms'
import { expectField } from '../lib/contract'

/*
 * 판정 결과 카드 (기획서 5.3).
 *
 * "규칙 N건"은 `ruleResult.matches` 길이다. `appliedRuleCodes` 길이가 아니다 —
 * 적용된 규칙(로드된 전부)과 매칭된 규칙(finding이 생성된 것)은 다르며,
 * D1 중첩 억제로 매칭됐으나 finding이 없는 규칙은 appliedRuleCodes에만 남는다 (계약서 §1-4).
 */
const props = defineProps({
  /** POST /messages 응답 본문 (200 / 202 / 403 모두 같은 필드 집합) */
  verdict: { type: Object, required: true },
  /** 작성자가 친 원문. 전송본과 다를 때만 비교해 보여준다 */
  originalText: { type: String, default: '' },
})

const matches = computed(() => {
  const value = props.verdict.ruleResult?.matches
  if (!expectField(Array.isArray(value), 'VerdictCard', 'ruleResult.matches가 배열이 아닙니다', props.verdict)) {
    return []
  }
  return value
})

const policies = computed(() => {
  const snapshot = props.verdict.policySnapshot
  if (!snapshot) {
    expectField(false, 'VerdictCard', 'policySnapshot이 응답에 없습니다 (계약서 §1-4는 4개 상태 모두 포함)', props.verdict)
    return []
  }
  return snapshot.policies ?? []
})

/*
 * 엠바고로 막힌 매칭. 해제일이 있으면 "언제 다시 시도하면 되는지"를 알려줘야 한다 —
 * 이유 없는 차단은 사용자가 우회를 학습하게 만들고, 그게 이 시스템의 최대 실패 모드다.
 * 규칙이 여러 건이면 가장 늦게 풀리는 날이 실질적인 재시도 시점이다.
 */
const embargoUntil = computed(() => {
  const dates = matches.value.map((m) => m.embargoUntil).filter(Boolean)
  return dates.length === 0 ? null : dates.slice().sort().at(-1)
})

/*
 * 전송이 승인된 본문. ALLOW면 원문 그대로, MASK면 라벨로 치환된 본문이다.
 * BLOCK은 null이고 PENDING은 아직 확정 전이라 내보낼 수 없다.
 */
const approvedText = computed(() => {
  const d = props.verdict.decision
  if (d !== 'ALLOW' && d !== 'MASK') return null
  return props.verdict.submittedText ?? null
})

/*
 * 상용 LLM은 게이트웨이가 대신 호출하지 않는다 (기획서 0.3 — 실제 LLM 호출은 범위 밖).
 * 승인된 본문을 클립보드에 넣고 해당 서비스를 열어 사람이 붙여넣는다.
 *
 * 본문을 URL 쿼리에 실어 보내지 않는 것은 의도다. 게이트웨이가 승인한 본문이라도
 * 주소창·브라우저 기록·리퍼러에 남기면 통제한 경로 밖으로 한 번 더 새는 셈이다.
 */
/*
 * 전송본이 원문과 달라졌을 때만 따로 보여준다. 허용은 둘이 같아서 한 번 더 그리면
 * 같은 문장이 두 번 나오는 소음이 된다.
 */
const changedText = computed(() => {
  const sent = props.verdict.submittedText
  if (!sent || sent === props.originalText) return null
  return sent
})

const EXTERNAL_LLMS = [
  { id: 'chatgpt', name: 'ChatGPT', url: 'https://chatgpt.com/' },
  { id: 'claude', name: 'Claude', url: 'https://claude.ai/new' },
  { id: 'gemini', name: 'Gemini', url: 'https://gemini.google.com/app' },
  { id: 'grok', name: 'Grok', url: 'https://grok.com/' },
]

const copied = ref(false)
const targetId = ref(EXTERNAL_LLMS[0].id)

async function sendToSelected() {
  const text = approvedText.value
  const target = EXTERNAL_LLMS.find((l) => l.id === targetId.value)
  if (!text || !target) return
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => (copied.value = false), 2400)
  } catch {
    // 클립보드가 막힌 환경(비 HTTPS 등)에서도 창은 열어준다. 사용자가 직접 복사한다.
    copied.value = false
  }
  window.open(target.url, '_blank', 'noopener,noreferrer')
}

const isAllow = computed(() => props.verdict.decision === 'ALLOW')
const isBlock = computed(() => props.verdict.decision === 'BLOCK')
const isMask = computed(() => props.verdict.decision === 'MASK')

const summary = computed(() => {
  switch (props.verdict.decision) {
    case 'ALLOW':
      return '전송됨'
    case 'MASK':
      return '마스킹 후 전송됨'
    case 'BLOCK':
      return '전송이 차단되었습니다. 내용을 수정한 뒤 재전송하세요.'
    case 'PENDING':
      return '보안 검토가 필요한 내용입니다.'
    default:
      return ''
  }
})
</script>

<template>
  <section class="verdict" :class="`decision-${verdict.decision.toLowerCase()}`">
    <header class="head">
      <StatusBadge :value="verdict.decision" />
      <span class="rule-count">규칙 {{ matches.length }}건</span>
      <span class="summary">{{ summary }}</span>
    </header>

    <!-- 허용은 규칙 0건이라 표를 그리지 않고 1줄로 축약한다 (화면 명세 2.2 S2) -->
    <ul v-if="!isAllow && matches.length > 0" class="rules">
      <!--
           key에 span을 붙인다. 같은 규칙이 여러 번 걸릴 수 있어 code만으로는 중복된다 —
           고객 명단 규칙이 한 문장에서 여러 명을 잡는 경우가 그것이다.
        -->
        <li v-for="match in matches" :key="`${match.code}:${match.span?.[0]}`" class="rule">
        <span class="code">{{ match.code }}</span>
        <span class="category">{{ term(CATEGORY_TERMS, match.category) }}</span>
        <span class="action" :class="`action-${(match.action ?? '').toLowerCase()}`">
          {{ term(ACTION_TERMS, match.action) }}
        </span>
        <span v-if="match.matchedKeyword" class="keyword">‘{{ match.matchedKeyword }}’</span>
        <span v-if="match.embargoUntil" class="until">{{ match.embargoUntil }} 해제</span>
        <span class="spacer" />
        <span class="obligation">{{ term(OBLIGATION_TERMS, match.obligation) }}</span>
        <span class="source">{{ match.source }}</span>
      </li>
    </ul>

    <p v-if="isBlock && embargoUntil" class="hint embargo">
      <strong>{{ embargoUntil }}</strong>부터 공개할 수 있는 내용입니다. 그때까지는 외부 AI로 보낼 수 없습니다.
      해당 표현을 빼고 다시 전송하세요. 입력창에 방금 입력한 내용이 그대로 남아 있습니다.
    </p>
    <p v-else-if="isBlock" class="hint">
      차단된 항목을 제거하거나 대체한 뒤 다시 전송하세요. 입력창에 방금 입력한 내용이 그대로 남아 있습니다.
    </p>
    <p v-if="isMask" class="hint">
      탐지된 개인정보를 라벨로 치환한 본문만 전송되었습니다.
    </p>

    <!-- 원문은 위 버블에 그대로 있고, 실제로 나간 본문은 여기에 있다 -->
    <div v-if="changedText" class="sent">
      <span class="sent-label">실제 전송된 본문</span>
      <p class="sent-body"><MaskedText :text="changedText" /></p>
    </div>

    <footer v-if="policies.length > 0" class="policies caption">
      적용 정책
      <span v-for="policy in policies" :key="policy.policyId" class="policy">
        {{ policy.code }} v{{ policy.version }}
      </span>
    </footer>

    <!-- 승인된 본문만 밖으로 나갈 수 있다. 차단·검토 대기에는 이 줄이 뜨지 않는다 -->
    <div v-if="approvedText" class="egress">
      <span class="egress-label">
        전송 승인됨{{ isMask ? ' · 마스킹 적용본' : '' }}
        <span v-if="copied" class="copied" role="status">— 복사했습니다</span>
      </span>
      <span class="egress-actions">
        <LlmPicker v-model="targetId" :options="EXTERNAL_LLMS" />
        <button type="button" class="egress-btn" @click="sendToSelected">프롬프트 입력</button>
      </span>
    </div>
  </section>
</template>

<style scoped>
.verdict {
  margin-top: 10px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--gray);
  border-radius: 8px;
  background: var(--card);
}

.decision-allow {
  border-left-color: var(--green);
}
.decision-mask {
  border-left-color: var(--amber);
}
.decision-block {
  border-left-color: var(--red);
}
.decision-pending {
  border-left-color: var(--purple);
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.rule-count {
  font-weight: 700;
  font-size: 15px;
}

.summary {
  color: var(--gray);
  font-size: var(--font-caption);
}

.rules {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  border-top: 1px solid var(--border);
}

.rule {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
}

.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
  color: var(--blue);
}

.category {
  color: var(--navy);
}

.action {
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 600;
  color: var(--gray);
  background: #fff;
  border: 1px solid var(--border-strong);
}

.action-block {
  color: var(--red);
  border-color: var(--red);
}
.action-mask {
  color: var(--amber);
  border-color: var(--amber);
}
.action-review {
  color: var(--purple);
  border-color: var(--purple);
}

.keyword {
  color: var(--purple);
}

.until {
  padding: 1px 6px;
  border: 1px solid var(--navy);
  border-radius: 3px;
  color: var(--navy);
  font-variant-numeric: tabular-nums;
}

.hint.embargo strong {
  font-variant-numeric: tabular-nums;
}

.spacer {
  flex: 1;
}

.obligation {
  color: var(--gray);
}

.source {
  color: var(--gray);
}

.sent {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--amber);
  border-radius: 6px;
  background: #fff;
}

.sent-label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--gray);
}

.sent-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
}

.hint {
  margin: 10px 0 0;
  font-size: var(--font-caption);
  color: var(--navy);
}

.egress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
  font-size: var(--font-caption);
}

.egress-label {
  color: var(--navy);
}

.egress-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.egress-btn {
  border-radius: 5px;
  padding: 5px 13px;
  border: 0;
  background: var(--navy);
  color: #fff;
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
}

.egress-btn:hover {
  background: var(--blue);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.copied {
  color: var(--green);
}

.policies {
  margin-top: 10px;
}

.policy {
  margin-left: 6px;
  color: var(--blue);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
