<script setup>
/*
 * 판정 집계. 화면에 쌓인 entries에서 센다 — 서버에 묻지 않는다.
 * 감사 콘솔의 전체 통계와 다른 값이며, 여기 있는 것은 지금 열린 대화의 것이다.
 *
 * 데모 대화를 열어 두고도 "이번 세션"이라고 부르면 숫자가 거짓이 된다. 열린 것이
 * 내 대화가 아닐 때는 제목을 바꾼다.
 */
import { computed } from 'vue'
import { STATUS_TERMS } from '../lib/terms'
import { entryVerdicts } from '../lib/decision'

const props = defineProps({
  entries: { type: Array, required: true },
  /** 화면에 뜬 것이 내가 입력한 대화인지 */
  own: { type: Boolean, default: true },
})

const ORDER = ['ALLOW', 'MASK', 'BLOCK', 'PENDING']

/*
 * 턴을 대표하는 판정으로 센다. 보낼 때의 판정만 세면 담당자가 확정한 뒤에도 "검토 대기
 * 1"로 남고, 답변이 막힌 턴도 "허용"으로 셌다 — 대화에는 차단이라 떠 있는데 위 숫자만
 * 딴말을 한다. 계산은 lib/decision 하나에 있다.
 */
/*
 * 검사 단위로 센다. 한 발화에 검사가 둘일 수 있어서(보낸 것·받은 것) 턴당 하나만 세면
 * 둘 중 심한 것만 남고 나머지가 숫자에서 사라진다 — 마스킹해서 보냈는데 답변이 검토로
 * 가면 화면에는 "규칙 3건 마스킹"이 떠 있는데 집계는 "마스킹 0"이 된다.
 */
const counts = computed(() => {
  const all = props.entries.flatMap((e) => entryVerdicts(e))
  return ORDER.map((decision) => ({
    decision,
    label: STATUS_TERMS[decision].label,
    token: STATUS_TERMS[decision].token,
    n: all.filter((d) => d === decision).length,
  }))
})
</script>

<template>
  <div v-if="entries.length > 0" class="tally">
    <span class="title">{{ own ? '이번 세션 판정' : '이 대화 판정' }}</span>
    <span v-if="!own" class="demo">(demo)</span>
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
  font-size: 13px;
}

.title {
  color: var(--gray);
}

.demo {
  color: var(--gray);
  font-size: 12px;
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
