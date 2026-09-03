<script setup>
/*
 * 전송 대상 모델 표시.
 *
 * **선택지가 하나뿐이라 드롭다운으로 만들지 않는다.** 눌러도 아무것도 바뀌지 않는
 * 컨트롤은 시연 중에 눌릴 자리이고, 없는 기능을 있는 것처럼 보이게 한다.
 * 모델이 늘면 MODELS 배열에 추가하는 것만으로 select로 바뀐다.
 *
 * 실제 호출은 지금 Mock이다 (기획서 0.4). 여기 표시는 "어디로 나가는가"를 알리는
 * 정보이지 사용자의 선택이 아니다.
 */
const MODELS = [{ id: 'llama-3.1-70b', name: 'Llama-3.1-70B', host: '사내 GPU' }]

const single = MODELS.length === 1 ? MODELS[0] : null
</script>

<template>
  <span v-if="single" class="model" :title="`전송 대상: ${single.name} (${single.host})`">
    <span class="dot" aria-hidden="true" />
    <span class="name">{{ single.name }}</span>
    <span class="host">{{ single.host }}</span>
  </span>
  <select v-else class="model-select">
    <option v-for="m in MODELS" :key="m.id" :value="m.id">{{ m.name }} · {{ m.host }}</option>
  </select>
</template>

<style scoped>
.model {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 11px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
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
  border-radius: 999px;
  font: inherit;
  font-size: 12px;
}
</style>
