<script setup>
/*
 * 입력창 (기획서 5.3) — textarea 3줄 + 전송 버튼. 전송 중 비활성.
 *
 * 값은 부모(ChatView)가 들고 있다. S4 차단 시 입력창에 원문을 복원해야 하는데,
 * 그 원문은 서버 응답이 아니라 클라이언트가 들고 있던 입력값이기 때문이다 (화면 명세 2.4-5).
 */
import { ref } from 'vue'
import { extractFromFile, headerLine, isSupported } from '../lib/spreadsheet'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit', 'attached'])

/*
 * 표 파일 붙이기 (D17).
 *
 * **파일은 브라우저 밖으로 나가지 않는다.** 여기서 텍스트만 뽑아 입력창에 넣고, 그
 * 텍스트가 기존 전송 경로를 그대로 탄다 — 첨부 업로드 API도 백엔드 파서도 없다.
 *
 * 곧바로 보내지 않고 입력창을 채우는 것이 핵심이다. 무엇이 나가는지 사람이 눈으로
 * 보고 보내야 한다. 게이트웨이가 검사하는 것도 바로 그 문자열이다.
 */
const picker = ref(null)
const reading = ref(false)
const fileError = ref('')

function pick() {
  fileError.value = ''
  picker.value?.click()
}

async function onFile(event) {
  const file = event.target.files?.[0]
  event.target.value = '' // 같은 파일을 다시 골라도 change가 오게
  if (!file) return

  if (!isSupported(file)) {
    // 방어가 아니라 안내다. 파일은 어차피 이 브라우저에서만 열린다.
    fileError.value = '표 파일만 읽을 수 있습니다 (xlsx, xls, csv, tsv).'
    return
  }

  reading.value = true
  fileError.value = ''
  try {
    const { text, meta } = await extractFromFile(file)
    if (text.trim().length === 0) {
      fileError.value = '내용이 비어 있는 표입니다.'
      return
    }
    const head = headerLine(meta)
    const before = props.modelValue.trimEnd()
    emit('update:modelValue', before.length > 0 ? `${before}\n\n${head}\n${text}` : `${head}\n${text}`)
    emit('attached', meta)
  } catch (err) {
    fileError.value = err?.message ?? '파일을 읽지 못했습니다.'
  } finally {
    reading.value = false
  }
}

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
    <input
      ref="picker"
      type="file"
      class="picker"
      accept=".xlsx,.xlsm,.xls,.csv,.tsv,.txt"
      @change="onFile"
    />
    <textarea
      rows="3"
      :value="modelValue"
      :disabled="disabled"
      placeholder="AI에게 보낼 프롬프트를 입력하세요. (Enter 전송, Shift + Enter 줄바꿈)"
      @input="emit('update:modelValue', $event.target.value)"
      @keydown="onKeydown"
    />
    <div class="actions">
      <button
        type="button"
        class="attach"
        :disabled="disabled || reading"
        title="표 파일에서 텍스트만 뽑아 입력창에 넣습니다. 파일 자체는 전송되지 않습니다."
        @click="pick"
      >
        {{ reading ? '읽는 중…' : '표 첨부' }}
      </button>
      <button
        type="button"
        class="send"
        :disabled="disabled || modelValue.trim().length === 0"
        @click="emit('submit')"
      >
        {{ disabled ? '전송 중…' : '전송' }}
      </button>
    </div>
  </div>

  <p v-if="fileError" class="file-error">{{ fileError }}</p>
</template>

<style scoped>
.message-input {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.picker {
  display: none;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.attach {
  padding: 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  background: #fff;
  color: var(--navy);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}

.attach:disabled {
  color: var(--gray);
  cursor: default;
}

.file-error {
  margin: 6px 2px 0;
  color: var(--red);
  font-size: 12.5px;
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
  flex: 1;
  width: 96px;
  border: 0;
  border-radius: 6px;
  background: var(--blue);
  color: #fff;
  font-weight: 600;
}
</style>
