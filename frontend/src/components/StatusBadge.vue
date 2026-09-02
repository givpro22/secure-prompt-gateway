<script setup>
import { computed } from 'vue'
import { STATUS_TERMS } from '../lib/terms'
import { contractViolation } from '../lib/contract'

/*
 * 내부 enum → 화면 용어 변환은 여기 한 곳에서만 한다 (기획서 5.6).
 * decision / message.status / finding.reviewStatus 세 계열을 모두 받는다.
 */
const props = defineProps({
  value: { type: String, default: null },
  /** 배지 앞에 붙는 꼬리표. 예: "최종 판정" */
  prefix: { type: String, default: '' },
})

const term = computed(() => {
  if (!props.value) return null
  const found = STATUS_TERMS[props.value]
  if (!found) {
    contractViolation('StatusBadge', `계약서 §3에 없는 enum 값: ${props.value}`)
    return null
  }
  return found
})
</script>

<template>
  <span v-if="term" class="badge" :style="{ '--badge-color': `var(--${term.token})` }">
    <span v-if="prefix" class="badge-prefix">{{ prefix }}</span>{{ term.label }}
  </span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--badge-color);
  border-radius: 4px;
  background: var(--card);
  color: var(--badge-color);
  font-size: var(--font-caption);
  font-weight: 600;
  white-space: nowrap;
  /* 색상만으로 구분하지 않도록 한글 라벨을 항상 함께 둔다 (화면 명세 1.2) */
}

.badge-prefix {
  margin-right: 4px;
  font-weight: 500;
  opacity: 0.75;
}
</style>
