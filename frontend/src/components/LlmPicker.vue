<script setup>
/*
 * 승인 본문을 내보낼 상용 서비스 선택기.
 *
 * 네이티브 select은 옵션 안에 이미지를 못 넣어서 직접 만들었다. 로고는 각 서비스의
 * 마크를 단순화한 도형이며 브랜드 자산 원본이 아니다 — 목록에서 서비스를 알아보게
 * 하는 용도다.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/* 각 서비스 마크를 단순화한 도형. 원본 브랜드 자산이 아니라 목록에서 알아보게 하는 용도다 */
const MARKS = {
  // 육각 매듭을 단순화한 육각형 테두리
  chatgpt:
    'M8 1 14 4.5v7L8 15 2 11.5v-7L8 1Zm0 1.6L3.4 5.3v5.4L8 13.4l4.6-2.7V5.3L8 2.6Zm0 2.1 2.8 1.6v3.4L8 10.3 5.2 8.7V5.3L8 4.7Z',
  // 방사형 별표
  claude:
    'M7.3 1h1.4v5.1l3.6-3.6 1 1-3.6 3.6H14.7v1.4H9.7l3.6 3.6-1 1-3.6-3.6V15H7.3V9.5l-3.6 3.6-1-1 3.6-3.6H1.3V7.1h5L2.7 3.5l1-1L7.3 6.1V1Z',
  // 네 꼭짓점 반짝임
  gemini:
    'M8 0.6c.7 3.9 2.8 6 6.7 6.7v1.4c-3.9.7-6 2.8-6.7 6.7h-1.4c-.7-3.9-2.8-6-6.7-6.7V7.3c3.9-.7 6-2.8 6.7-6.7h1.4Z',
}

const props = defineProps({
  options: { type: Array, required: true },
  modelValue: { type: String, required: true },
})
const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)

const selected = computed(
  () => props.options.find((o) => o.id === props.modelValue) ?? props.options[0],
)

function choose(id) {
  emit('update:modelValue', id)
  open.value = false
}

function onOutside(event) {
  if (root.value && !root.value.contains(event.target)) open.value = false
}

function onKey(event) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('mousedown', onOutside)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutside)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div ref="root" class="picker">
    <button
      type="button"
      class="trigger"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="open = !open"
    >
      <span class="logo" :class="`lg-${selected.id}`" aria-hidden="true">
        <svg viewBox="0 0 16 16" width="13" height="13">
          <path :d="MARKS[selected.id]" fill="currentColor" />
        </svg>
      </span>
      <span class="label">{{ selected.name }}</span>
      <span class="caret" :class="{ up: open }" aria-hidden="true">▾</span>
    </button>

    <ul v-if="open" class="menu" role="listbox">
      <li v-for="o in options" :key="o.id">
        <button
          type="button"
          class="option"
          role="option"
          :aria-selected="o.id === modelValue"
          @click="choose(o.id)"
        >
          <span class="logo" :class="`lg-${o.id}`" aria-hidden="true">
            <svg viewBox="0 0 16 16" width="13" height="13">
              <path :d="MARKS[o.id]" fill="currentColor" />
            </svg>
          </span>
          <span class="label">{{ o.name }}</span>
        </button>
      </li>
    </ul>
  </div>
</template>


<style scoped>
.picker {
  position: relative;
}

.trigger,
.option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  font: inherit;
  font-size: 11.5px;
  color: var(--navy);
}

.trigger {
  padding: 4px 9px 4px 10px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: #fff;
}

.trigger:hover {
  border-color: var(--blue);
}

.logo {
  display: grid;
  place-items: center;
  width: 15px;
  height: 15px;
  flex: none;
}

.lg-chatgpt {
  color: #0f9d78;
}
.lg-claude {
  color: #c8613f;
}
.lg-gemini {
  color: #3b7de0;
}

.caret {
  color: var(--gray);
  font-size: 9px;
  transition: transform 140ms ease;
}

.caret.up {
  transform: rotate(180deg);
}

.menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 5px);
  z-index: 5;
  margin: 0;
  padding: 4px;
  list-style: none;
  min-width: 132px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 6px 18px rgb(22 32 46 / 12%);
}

.option {
  width: 100%;
  padding: 6px 8px;
  border-radius: 6px;
  text-align: left;
}

.option:hover {
  background: var(--card);
}

.option[aria-selected='true'] {
  font-weight: 700;
}
</style>
