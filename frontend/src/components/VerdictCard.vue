<script setup>
import { computed } from 'vue'
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
      <li v-for="match in matches" :key="match.code" class="rule">
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
      탐지된 개인정보를 라벨로 치환한 본문만 전송되었습니다. 위 발화에 표시된 노란 라벨이 치환 구간입니다.
    </p>

    <footer v-if="policies.length > 0" class="policies caption">
      적용 정책
      <span v-for="policy in policies" :key="policy.policyId" class="policy">
        {{ policy.code }} v{{ policy.version }}
      </span>
    </footer>
  </section>
</template>

<style scoped>
.verdict {
  margin-top: 8px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--gray);
  border-radius: 6px;
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
  gap: 8px;
  flex-wrap: wrap;
  padding: 7px 0;
  border-bottom: 1px solid var(--border);
  font-size: var(--font-caption);
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

.hint {
  margin: 10px 0 0;
  font-size: var(--font-caption);
  color: var(--navy);
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
