<script setup>
/*
 * 이번 세션 판정 집계. 화면에 쌓인 entries에서 센다 — 서버에 묻지 않는다.
 * 감사 콘솔의 전체 통계와 다른 값이며, 여기 있는 것은 "방금 내가 보낸 것들"이다.
 */
import { computed } from 'vue'
import { STATUS_TERMS } from '../lib/terms'

const props = defineProps({
  entries: { type: Array, required: true },
})

const ORDER = ['ALLOW', 'MASK', 'BLOCK', 'PENDING']

const counts = computed(() =>
  ORDER.map((decision) => ({
    decision,
    label: STATUS_TERMS[decision].label,
    token: STATUS_TERMS[decision].token,
    n: props.entries.filter((e) => e.verdict.decision === decision).length,
  })),
)
</script>

<template>
  <div v-if="entries.length > 0" class="tally">
    <span class="title">이번 세션 판정</span>
    <span v-for="c in counts" :key="c.decision" class="item" :class="`t-${c.token}`">
      <span class="dot" aria-hidden="true" />
      {{ c.label }} <strong>{{ c.n }}</strong>
    </span>
  </div>
</template>

<style scoped>
.tally {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding: 8px 0 14px;
  font-size: 12px;
}

.title {
  color: var(--gray);
}

.item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--navy);
  font-variant-numeric: tabular-nums;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.t-green .dot {
  background: var(--green);
}
.t-amber .dot {
  background: var(--amber);
}
.t-red .dot {
  background: var(--red);
}
.t-purple .dot {
  background: var(--purple);
}

strong {
  font-weight: 700;
}
</style>
