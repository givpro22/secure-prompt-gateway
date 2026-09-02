<script setup>
import { computed } from 'vue'
import StatusBadge from './StatusBadge.vue'
import { CATEGORY_TERMS, term } from '../lib/terms'

/*
 * AI 제안(후보) 목록.
 *
 * SCR-01(직원 화면)에서는 `readonly`로 쓴다. 직원 화면에 ACCEPT/REJECT를 두면
 * 책임 경계가 무너진다 — 확정은 SCR-02에서 보안 담당자만 한다 (기획서 4장).
 *
 * SCR-02에서는 `reviewStatus === 'SUGGESTED'`인 항목에만 버튼이 나온다 (D6).
 * 규칙 finding(CONFIRMED)은 이 컴포넌트에 들어오지 않는다.
 */
const props = defineProps({
  /** findings[] 중 source === 'AI'인 항목 */
  findings: { type: Array, default: () => [] },
  /** aiAssessment. 202 시점에는 존재하지 않으므로 COMPLETED 가드 안에서만 넘긴다 */
  assessment: { type: Object, default: null },
  readonly: { type: Boolean, default: true },
  busyFindingId: { type: Number, default: null },
})

const emit = defineEmits(['review'])

const missingContext = computed(() => props.assessment?.missingContext ?? [])

function canReview(finding) {
  return !props.readonly && finding.reviewStatus === 'SUGGESTED'
}
</script>

<template>
  <div class="candidates">
    <p v-if="findings.length === 0" class="caption empty">
      AI가 제시한 후보가 없습니다.
    </p>

    <ul v-else class="list">
      <li v-for="finding in findings" :key="finding.findingId" class="item">
        <header class="item-head">
          <span class="code">{{ finding.code }}</span>
          <span class="category">{{ term(CATEGORY_TERMS, finding.category) }}</span>
          <span class="spacer" />
          <StatusBadge :value="finding.reviewStatus" />
        </header>

        <p v-if="finding.rationale" class="rationale">{{ finding.rationale }}</p>

        <ul v-if="finding.evidence && finding.evidence.length > 0" class="evidence">
          <li v-for="(ev, index) in finding.evidence" :key="index">
            <span class="ev-source">{{ ev.source }}</span>
            <span v-if="ev.excerpt" class="ev-excerpt">{{ ev.excerpt }}</span>
          </li>
        </ul>

        <p v-if="finding.reviewedBy" class="caption reviewed">
          {{ finding.reviewedBy.name }} 확정 · {{ finding.reviewedAt }}
        </p>

        <!-- SUGGESTED일 때만 노출한다. 처리된 후보에는 버튼을 다시 그리지 않는다 -->
        <div v-if="canReview(finding)" class="actions">
          <button
            type="button"
            class="accept"
            :disabled="busyFindingId !== null"
            @click="emit('review', { finding, reviewStatus: 'ACCEPTED' })"
          >
            ACCEPT (위반 확정)
          </button>
          <button
            type="button"
            class="reject"
            :disabled="busyFindingId !== null"
            @click="emit('review', { finding, reviewStatus: 'REJECTED' })"
          >
            REJECT (기각)
          </button>
        </div>
      </li>
    </ul>

    <div v-if="missingContext.length > 0" class="missing">
      <p class="section-title">확인이 필요한 맥락</p>
      <ul>
        <li v-for="(text, index) in missingContext" :key="index" class="caption">{{ text }}</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.item {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--purple);
  border-radius: 6px;
  background: #fff;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
  color: var(--purple);
}

.category {
  font-size: var(--font-caption);
  color: var(--gray);
}

.spacer {
  flex: 1;
}

.rationale {
  margin: 6px 0 0;
  font-size: var(--font-caption);
}

.evidence {
  margin: 6px 0 0;
  padding-left: 16px;
  font-size: var(--font-caption);
  color: var(--gray);
}

.ev-source {
  color: var(--blue);
  font-weight: 600;
}

.ev-excerpt::before {
  content: ' — ';
}

.reviewed {
  margin: 6px 0 0;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.actions button {
  padding: 5px 12px;
  border-radius: 4px;
  font-size: var(--font-caption);
  font-weight: 600;
  background: #fff;
}

.accept {
  border: 1px solid var(--red);
  color: var(--red);
}

.reject {
  border: 1px solid var(--gray);
  color: var(--gray);
}

.missing {
  margin-top: 10px;
}

.missing ul {
  margin: 0;
  padding-left: 16px;
}

.empty {
  margin: 0;
}
</style>
