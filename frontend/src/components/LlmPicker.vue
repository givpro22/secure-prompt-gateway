<script setup>
/*
 * 승인 본문을 내보낼 상용 서비스 선택기.
 *
 * 네이티브 select은 옵션 안에 이미지를 못 넣어서 직접 만들었다.
 * 바깥 클릭과 Esc로 닫힌다.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import LlmMark from './LlmMark.vue'

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
      <LlmMark :id="selected.id" />
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
          <LlmMark :id="o.id" :size="14" />
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
  gap: 7px;
  border: 0;
  background: transparent;
  font: inherit;
  font-size: 12.5px;
  color: var(--navy);
}

.trigger {
  padding: 4px 9px 4px 10px;
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  background: #fff;
}

.trigger:hover {
  border-color: var(--blue);
}

.caret {
  color: var(--gray);
  font-size: 10px;
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
  min-width: 138px;
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
