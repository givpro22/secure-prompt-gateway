<script setup>
import MaskedText from './MaskedText.vue'

/*
 * 직원 발화 버블. **작성자가 친 원문 그대로** 그린다.
 *
 * 마스킹본을 여기 그리면 무엇을 물어봤는지 알아볼 수 없다 — 라벨로 바뀐 자리가 문장의
 * 핵심일 때가 많다. 실제로 나간 본문은 판정 카드가 따로 보여준다.
 *
 * 이 값은 API가 아니라 FE 로컬 상태에서 온다. 원문 미표시 원칙(5.4)의 대상은 감사
 * 콘솔에서 보는 타인의 원문이며, 작성자에게 자기 입력을 돌려주는 것은 유출이 아니다 (D15).
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
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  font-size: 15.5px;
  line-height: 1.75;
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
