<script setup>
/*
 * 전송 대상 모델 표시.
 *
 * **화면이 지어내지 않는다.** 예전에는 "Llama-3.1-70B · 사내 GPU"를 하드코딩해 두었다.
 * 실제로 부르는 것이 없던 시절에는 "어디로 나갈 것인가"를 알리는 말이었지만, 게이트웨이가
 * 진짜로 모델을 부르기 시작한 뒤로는 화면이 거짓말을 하는 자리가 되었다 — 답은 제미나이가
 * 주는데 칩은 사내 GPU라고 적혀 있었다.
 *
 * 이제 서버에 묻는다(`GET /messages/answer/available`). 무엇이 붙어 있는지는 환경변수가
 * 정하고 코드는 모른다 — 그 사실을 화면도 같은 방식으로 안다.
 *
 * 제공자가 꺼져 있으면 "미연결"로 적는다. 아무것도 안 붙은 상태를 붙은 것처럼 보이게 하는
 * 것이 이 서비스가 막으려는 종류의 거짓이다.
 */
import { computed, onMounted, ref } from 'vue'
import { fetchAnswerAvailable } from '../api/messages'

/*
 * 한 번만 묻는다. 칩은 대화마다 그려지는데 매번 물으면 같은 답을 수십 번 받는다.
 * 모듈에 약속을 담아 두고 모두가 그것을 기다린다.
 */
let inflight = null
function load() {
  if (!inflight) inflight = fetchAnswerAvailable().catch(() => ({ available: false, provider: '' }))
  return inflight
}

const state = ref({ available: false, provider: '' })
onMounted(async () => {
  state.value = await load()
})

const title = computed(() =>
  state.value.available
    ? `답변 제공자: ${state.value.provider}. 게이트웨이가 마스킹본을 보내고 받은 답을 다시 검사합니다.`
    : '연결된 모델이 없습니다. 받은 답변을 붙여넣으면 같은 정책으로 검사합니다.',
)
</script>

<template>
  <span class="model" :class="{ off: !state.available }" :title="title">
    <span class="dot" aria-hidden="true" />
    <span class="name">{{ state.available ? state.provider : '모델 미연결' }}</span>
    <span class="host">{{ state.available ? '게이트웨이 경유' : '붙여넣기로 검사' }}</span>
  </span>
</template>

<style scoped>
.model {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 11px;
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  background: #fff;
  font-size: 13px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
}

.name {
  font-weight: 600;
  color: var(--navy);
}

.host {
  color: var(--gray);
}

.model-select {
  padding: 5px 10px;
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  font: inherit;
  font-size: 13px;
}

/* 붙은 것이 없을 때는 점을 죽인다. 켜진 것과 같은 색이면 상태를 못 읽는다 */
.model.off {
  color: var(--gray);
}

.model.off .dot {
  background: var(--gray);
}
</style>
