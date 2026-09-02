<script setup>
/*
 * S5-a — 폴링 중 표시 (기획서 5.3).
 * Mock 지연 2.5초(`ai.mock.delay-ms`) 동안 실제로 보이는 화면이며,
 * 202 비동기 설계가 화면에 드러나는 유일한 자리다 (9.5).
 *
 * **"보안 검토 중"은 D16의 "분석" 규칙 대상이 아니다. 고치지 말 것.**
 * D16은 `aiStatus` **값 라벨**을 "분석"으로 고정한 것이고(AI의 상태와 사람의 절차를 가르기 위해),
 * 이 문장은 기획서 5.3이 고정한 **프로세스 안내 문구**다. 직원에게 필요한 것은
 * 자기 프롬프트가 보안 검토를 받고 있다는 사실이지 AI 개입 여부가 아니다.
 * 기준: 값 라벨은 행위 주체를 구분하고, 안내 문장은 기획서 문구를 따른다.
 */
defineProps({
  elapsedSec: { type: Number, default: 0 },
  attempts: { type: Number, default: 0 },
})
</script>

<template>
  <div class="pending">
    <span class="spinner" aria-hidden="true" />
    <span class="label">보안 검토 중</span>
    <span class="caption">{{ elapsedSec }}초 경과 · 폴링 {{ attempts }}회</span>
  </div>
</template>

<style scoped>
.pending {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--purple);
  border-radius: 6px;
  background: var(--card);
}

.label {
  font-weight: 700;
  color: var(--purple);
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid color-mix(in srgb, var(--purple) 30%, transparent);
  border-top-color: var(--purple);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2.4s;
  }
}
</style>
