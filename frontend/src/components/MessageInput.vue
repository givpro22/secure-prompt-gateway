<script setup>
/*
 * 입력창 (기획서 5.3) — textarea 3줄 + 전송 버튼. 전송 중 비활성.
 *
 * 값은 부모(ChatView)가 들고 있다. S4 차단 시 입력창에 원문을 복원해야 하는데,
 * 그 원문은 서버 응답이 아니라 클라이언트가 들고 있던 입력값이기 때문이다 (화면 명세 2.4-5).
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ACCEPT_ATTR,
  ACCEPT_LABEL,
  ACCEPT_NAMES,
  extractFromFile,
  headerLine,
  isSupported,
} from '../lib/spreadsheet'

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

function onFile(event) {
  const file = event.target.files?.[0]
  event.target.value = '' // 같은 파일을 다시 골라도 change가 오게
  if (file) handleFile(file)
}

/*
 * 끌어다 놓기. 상용 챗이 다 그렇게 되어 있어서 사람들이 먼저 그렇게 해 본다.
 *
 * dragenter/leave가 자식 요소를 지날 때마다 번갈아 오므로 깊이를 센다 — 안 세면
 * textarea 위를 지나는 순간 강조가 깜빡인다.
 */
const dropDepth = ref(0)
const dropping = computed(() => dropDepth.value > 0)

/** 파일을 끌고 오는 중인가. 글자를 끌 때는(textarea 안 선택 이동) 끼어들지 않는다 */
function hasFiles(event) {
  return [...(event.dataTransfer?.types ?? [])].includes('Files')
}

/*
 * 받는 자리는 **창 전체**다.
 *
 * 입력창 위에만 걸었더니 조금만 빗나가도 브라우저가 그 파일을 열어 화면이 통째로
 * 바뀌었다. 끌어다 놓는 사람은 창 가운데를 겨냥하지 입력창을 겨냥하지 않는다.
 *
 * 창 어디에 놓아도 받고, 어디에 놓아도 브라우저가 파일을 열지 않게 막는다 — 이 화면에
 * 파일을 떨어뜨릴 다른 자리는 없다.
 */
function onWindowDragEnter(event) {
  if (!hasFiles(event)) return
  event.preventDefault()
  dropDepth.value += 1
}

function onWindowDragOver(event) {
  if (!hasFiles(event)) return
  // 이걸 막지 않으면 놓는 순간 브라우저가 파일을 연다
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}

function onWindowDragLeave(event) {
  if (!hasFiles(event)) return
  dropDepth.value = Math.max(0, dropDepth.value - 1)
}

function onWindowDrop(event) {
  if (!hasFiles(event)) return
  event.preventDefault()
  dropDepth.value = 0
  const file = event.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

onMounted(() => {
  window.addEventListener('dragenter', onWindowDragEnter)
  window.addEventListener('dragover', onWindowDragOver)
  window.addEventListener('dragleave', onWindowDragLeave)
  window.addEventListener('drop', onWindowDrop)
})

onBeforeUnmount(() => {
  window.removeEventListener('dragenter', onWindowDragEnter)
  window.removeEventListener('dragover', onWindowDragOver)
  window.removeEventListener('dragleave', onWindowDragLeave)
  window.removeEventListener('drop', onWindowDrop)
})

async function handleFile(file) {
  if (props.disabled || reading.value) return

  if (!isSupported(file)) {
    // 방어가 아니라 안내다. 파일은 어차피 이 브라우저에서만 열린다.
    fileError.value = `표 파일만 읽을 수 있습니다 (${ACCEPT_LABEL}).`
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
  <div class="message-input" :class="{ dropping }">
    <input
      ref="picker"
      type="file"
      class="picker"
      :accept="ACCEPT_ATTR"
      @change="onFile"
    />

    <!--
      ＋는 입력창 안, 글 아래 줄에 둔다. 상용 챗이 다 그 구조다.
      옆에 두고 왼쪽 여백을 비우면 글이 그만큼 들여쓰기 돼서 가운데 정렬처럼 보인다.
    -->
    <div class="field">
      <textarea
        rows="3"
        :value="modelValue"
        :disabled="disabled"
        placeholder="AI에게 보낼 프롬프트를 입력하세요. (Enter 전송, Shift + Enter 줄바꿈)"
        @input="emit('update:modelValue', $event.target.value)"
        @keydown="onKeydown"
      />

      <div class="tools">
        <button
          type="button"
          class="plus"
          :disabled="disabled || reading"
          :aria-label="reading ? '읽는 중' : '표 파일 첨부'"
          title="표 파일에서 텍스트만 뽑아 입력창에 넣습니다. 파일 자체는 전송되지 않습니다."
          @click="pick"
        >
          <span v-if="reading" class="spin" aria-hidden="true" />
          <span v-else aria-hidden="true">＋</span>
        </button>
        <!--
          받는 형식은 ＋에 손이 갔을 때만 편다. 늘 펴 두면 입력창 아래가 늘 시끄럽고,
          정작 필요한 순간은 첨부를 누르려는 그 순간뿐이다.

          읽는 중에는 상태가 우선이다 — 그때는 형식 목록이 알려 줄 것이 없다.
        -->
        <span v-if="reading" class="formats reading">읽는 중…</span>
        <span v-else class="formats" aria-hidden="true">
          <span
            v-for="(name, i) in ACCEPT_NAMES"
            :key="name"
            class="fmt"
            :style="{ transitionDelay: `${i * 30}ms` }"
          >{{ name }}</span>
        </span>
      </div>

    </div>
    <button
      type="button"
      class="send"
      :disabled="disabled || modelValue.trim().length === 0"
      @click="emit('submit')"
    >
      {{ disabled ? '전송 중…' : '전송' }}
    </button>

  </div>

  <p v-if="fileError" class="file-error">{{ fileError }}</p>

  <!--
    받는 자리가 창 전체이므로 안내도 창 전체에 띄운다. 입력창에만 작게 띄우면
    "저기까지 가져가야 하나" 싶어 사람이 다시 조준한다.
  -->
  <Teleport to="body">
    <Transition name="veil">
      <div v-if="dropping" class="drop-veil">
        <div class="drop-card">
          <span class="drop-title">여기에 놓으세요</span>
          <span class="drop-sub">표에서 텍스트만 뽑아 입력창에 넣습니다 · 파일은 전송되지 않습니다</span>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.message-input {
  position: relative;
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.picker {
  display: none;
}

/*
 * 상용 챗의 그 자리 — 입력창 **안** 왼쪽 아래. 테두리 없는 동그라미로 두어 입력창
 * 안의 도구처럼 보이게 한다. 테두리를 주면 상자 안에 상자가 하나 더 생긴다.
 */
.plus {
  flex: none;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--gray);
  font-size: 17px;
  line-height: 1;
  cursor: pointer;
}

.plus:hover:not(:disabled) {
  background: var(--card);
  color: var(--blue);
}

.spin {
  width: 14px;
  height: 14px;
  border: 2px solid var(--border-strong);
  border-top-color: var(--blue);
  border-radius: 50%;
  animation: plus-spin 0.7s linear infinite;
}

@keyframes plus-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 끌고 들어오면 입력창 전체가 받는 자리임을 보인다 */
.dropping textarea {
  border-color: var(--blue);
}

.plus:disabled {
  color: var(--border-strong);
}

/* 아래는 body로 옮겨 그리는 판이라 scoped 밖에 둔다 */
</style>

<style>
.drop-veil {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  background: rgb(22 32 46 / 34%);
  /* 마우스를 가로채면 안 된다. 이 판 아래로 drop 이벤트가 그대로 지나가야 한다 */
  pointer-events: none;
}

.drop-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding: 26px 34px;
  border: 2px dashed var(--blue);
  border-radius: 12px;
  background: #fff;
  text-align: center;
}

.drop-title {
  color: var(--navy);
  font-size: 16px;
  font-weight: 600;
}

.drop-sub {
  color: var(--gray);
  font-size: 12.5px;
}

.veil-enter-active,
.veil-leave-active {
  transition: opacity 0.14s ease;
}

.veil-enter-active .drop-card,
.veil-leave-active .drop-card {
  transition: transform 0.16s cubic-bezier(0.2, 0.9, 0.3, 1);
}

.veil-enter-from,
.veil-leave-to {
  opacity: 0;
}

.veil-enter-from .drop-card,
.veil-leave-to .drop-card {
  transform: scale(0.96);
}

.file-error {
  margin: 6px 2px 0;
  color: var(--red);
  font-size: 12.5px;
}

/* 테두리는 상자가 갖는다. 안에 글과 도구 줄이 함께 산다 */
.field {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  background: #fff;
}

/*
 * 테두리도 배경도 갖지 않는다. 상자는 .field 하나뿐이어야 한다 — 여기에 테두리를
 * 주면 상자 안에 상자가 생겨 칸이 나뉘어 보인다.
 *
 * resize도 끈다. 오른쪽 아래 손잡이가 그 자체로 "여기까지가 다른 상자"라고 말하고,
 * 끌어서 늘리면 바깥 상자와 크기가 어긋난다.
 */
textarea {
  flex: 1;
  padding: 10px 12px 6px;
  border: 0;
  /* 브라우저 기본 초점 테두리를 끈다. 초점 표시는 바깥 상자가 대신 한다 */
  outline: none;
  resize: none;
  line-height: 1.6;
  background: transparent;
  font: inherit;
  color: inherit;
}


/* 글 아래 도구 줄. ＋와 받는 형식이 여기 산다 */
/*
 * 도구 줄. hover 영역을 이 줄 전체가 아니라 왼쪽 덩어리로 좁히려고 inline-flex로 둔다 —
 * 줄 전체면 입력창 아래를 스치기만 해도 형식이 펴져서 시끄럽다.
 */
.tools {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  padding: 0 8px 6px;
}

.formats {
  display: flex;
  gap: 4px;
  color: var(--gray);
  font-size: 11px;
  letter-spacing: 0.01em;
  /* 마우스를 여기 올려도 목록이 접히지 않게 — 그래도 클릭은 ＋가 받는다 */
  pointer-events: none;
}

.formats.reading {
  padding-left: 2px;
  font-size: 11.5px;
}

/*
 * 형식 하나가 알약 하나. 평소에는 접혀 있다가 ＋ 쪽에 손이 오면 왼쪽에서 차례로
 * 밀려 나온다. 자리를 미리 차지하지 않도록 폭까지 함께 접는다 — opacity만 낮추면
 * 보이지 않는 빈칸이 입력창 아래를 늘 차지한다.
 */
.fmt {
  max-width: 0;
  padding: 2px 0;
  overflow: hidden;
  border-radius: 4px;
  background: var(--card);
  opacity: 0;
  transform: translateX(-6px);
  white-space: nowrap;
  transition:
    max-width 0.22s ease,
    padding 0.22s ease,
    opacity 0.18s ease,
    transform 0.22s cubic-bezier(0.2, 0.9, 0.3, 1);
}

.tools:hover .fmt,
.plus:focus-visible ~ .formats .fmt {
  max-width: 60px;
  padding: 2px 6px;
  opacity: 1;
  transform: translateX(0);
}

/* 나갈 때는 한꺼번에 접는다. 들어올 때의 차례를 거꾸로 돌리면 굼떠 보인다 */
.tools:not(:hover) .fmt {
  transition-delay: 0ms !important;
}

@media (prefers-reduced-motion: reduce) {
  .fmt {
    transition-duration: 0.01ms;
    transform: none;
  }
}

/*
 * 초점 표시는 바깥 상자가 받는다.
 *
 * 전에는 textarea가 직접 파란 outline을 그렸다. 테두리가 하나뿐이던 시절에는 그것이
 * 곧 입력창의 테두리였는데, ＋ 줄이 생기면서 상자가 .field로 옮겨 간 뒤로는 상자 안에
 * 파란 상자가 하나 더 그려져 칸이 나뉘어 보였다.
 */
.field:focus-within {
  border-color: var(--blue);
  outline: 1px solid var(--blue);
  outline-offset: -2px;
}

.send {
  flex: none;
  width: 96px;
  border: 0;
  border-radius: 6px;
  background: var(--blue);
  color: #fff;
  font-weight: 600;
}
</style>
