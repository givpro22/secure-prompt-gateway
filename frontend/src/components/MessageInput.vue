<script setup>
/*
 * 입력창 (기획서 5.3) — textarea 3줄 + 전송 버튼. 전송 중 비활성.
 *
 * 값은 부모(ChatView)가 들고 있다. S4 차단 시 입력창에 원문을 복원해야 하는데,
 * 그 원문은 서버 응답이 아니라 클라이언트가 들고 있던 입력값이기 때문이다 (화면 명세 2.4-5).
 */
defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit'])

/*
 * Enter로 보내고 Shift+Enter로 줄을 바꾼다. ⌘/Ctrl+Enter도 그대로 둔다 — 손에 익은
 * 사람이 있고 막을 이유가 없다.
 *
 * `isComposing`을 먼저 본다. 한글은 조합 중에도 keydown이 오므로, 이것을 빼면
 * 글자를 확정하려고 누른 Enter가 전송이 되어 문장이 잘려 나간다.
 */
function onKeydown(event) {
  if (event.key !== 'Enter') return
  if (event.isComposing || event.keyCode === 229) return
  if (event.shiftKey) return
  event.preventDefault()
  emit('submit')
}
</script>

<template>
  <div class="message-input">
    <textarea
      rows="3"
      :value="modelValue"
      :disabled="disabled"
      placeholder="AI에게 보낼 프롬프트를 입력하세요. (Enter 전송, Shift + Enter 줄바꿈)"
      @input="emit('update:modelValue', $event.target.value)"
      @keydown="onKeydown"
    />
    <button
      type="button"
      class="send"
      :disabled="disabled || modelValue.trim().length === 0"
      @click="emit('submit')"
    >
      {{ disabled ? '전송 중…' : '전송' }}
    </button>
  </div>
</template>

<style scoped>
.message-input {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

textarea {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  resize: vertical;
  line-height: 1.6;
  background: #fff;
}

textarea:focus {
  outline: 2px solid var(--blue);
  outline-offset: -1px;
}

.send {
  width: 96px;
  border: 0;
  border-radius: 6px;
  background: var(--blue);
  color: #fff;
  font-weight: 600;
}
</style>
