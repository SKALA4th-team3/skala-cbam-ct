<script setup>
import { onEscape } from '@/composables/useEscape'

/** 덮개 하나. 등록 폼·확인·사유 입력이 전부 이걸 쓴다.
 *  `.ovl` 이라는 이름이 focusTrap 의 기준이라 바꾸면 트랩이 풀린다.
 *  닫는 길이 셋이다 — Esc · 배경 클릭 · 닫기 버튼. 하나만 있으면 누군가는 갇힌다. */
const props = defineProps({
  open: Boolean,
  title: String,
  sub: String,
  wide: Boolean,
  /** 바깥을 눌러도 안 닫힌다 — 적는 중인 폼이 날아가면 안 되는 자리 */
  sticky: Boolean,
})
const emit = defineEmits(['close'])
onEscape(() => { if (props.open) emit('close') })
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="ovl" role="dialog" aria-modal="true" :aria-label="title"
         @click.self="!sticky && emit('close')">
      <div class="ovlcard" :class="{ wide }">
        <div class="ovlhead">
          <div>
            <h3>{{ title }}</h3>
            <p v-if="sub" class="p">{{ sub }}</p>
          </div>
          <button class="cl" aria-label="닫기" @click="emit('close')">✕</button>
        </div>
        <div class="ovlbody"><slot /></div>
        <div v-if="$slots.acts" class="ovlacts"><slot name="acts" /></div>
      </div>
    </div>
  </Teleport>
</template>
