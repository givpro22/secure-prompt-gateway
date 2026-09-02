<script setup>
import { computed } from 'vue'
import { MASK_LABELS } from '../lib/terms'

/*
 * 마스킹 하이라이트 (D3).
 *
 * finding의 span은 원문 기준이라 마스킹본에 그대로 쓰면 하이라이트가 밀린다
 * (`900101-1234567` 14자 → `[주민번호]` 6자). 오프셋 산술을 하지 않고
 * `submittedText`에서 라벨 문자열을 검색해 <mark>로 감싼다.
 */
const props = defineProps({
  text: { type: String, default: '' },
})

/** 정규식 메타문자를 이스케이프한다. 라벨의 대괄호가 문자 클래스로 해석되면 안 된다. */
function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const LABEL_RE = new RegExp(MASK_LABELS.map(escapeRegExp).join('|'), 'g')

/** v-html을 쓰므로 사용자 입력을 먼저 escape한다. */
function escapeHtml(s) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const html = computed(() => {
  // escape가 먼저다. 라벨에는 HTML 특수문자가 없으므로 escape 후에도 문자열이 그대로 남는다.
  const safe = escapeHtml(props.text ?? '')
  return safe.replace(LABEL_RE, (label) => `<mark>${label}</mark>`)
})
</script>

<template>
  <span class="masked-text" v-html="html" />
</template>

<style scoped>
.masked-text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
