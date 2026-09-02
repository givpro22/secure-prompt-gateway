<script setup>
import MaskedText from './MaskedText.vue'

/*
 * 직원 발화 버블.
 *
 * 허용·마스킹·검토 대기는 서버가 돌려준 `submittedText`(마스킹 적용본)를 그린다.
 *
 * 차단은 `submittedText`가 `null`이므로(마스킹본이 생성된 적이 없다 — D5/D14)
 * **작성자 본인이 방금 입력한 텍스트**를 그린다 (D15). 원문 미표시 원칙(5.4)의 대상은
 * 감사 콘솔에서 보는 타인의 원문이며, 작성자에게 자기 입력을 돌려주는 것은 유출이 아니다.
 * 5.3이 이미 차단 시 입력창에 원문을 복원하라고 지시하므로 같은 텍스트가 이미 화면에 있고,
 * 버블만 가리면 일관되지 않는다. 이 값은 API가 아니라 FE 로컬 상태에서 온다.
 */
defineProps({
  text: { type: String, default: null },
  blocked: { type: Boolean, default: false },
})
</script>

<template>
  <div class="bubble" :class="{ blocked }">
    <span class="who">직원</span>
    <p class="body"><MaskedText :text="text ?? ''" /></p>
  </div>
</template>

<style scoped>
.bubble {
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.bubble.blocked {
  border-color: color-mix(in srgb, var(--red) 40%, var(--border));
}

.who {
  display: inline-block;
  margin-bottom: 4px;
  font-size: var(--font-caption);
  font-weight: 700;
  color: var(--gray);
}

.body {
  margin: 0;
}
</style>
