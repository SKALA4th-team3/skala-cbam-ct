<script setup>
import { useRouter } from 'vue-router'
import { onEscape } from '@/composables/useEscape'
import StatusChip from '@/components/StatusChip.vue'

/** 알림 서랍 — 19·22·51번의 「담당자에게 알린다」가 도착하는 자리.
 *  `.drawer` 라는 이름이 focusTrap 의 기준이라 바꾸면 트랩이 풀린다. */
const props = defineProps({ open: Boolean, items: { type: Array, default: () => [] }, loadedAt: Date })
const emit = defineEmits(['close'])
const router = useRouter()
onEscape(() => { if (props.open) emit('close') })
function go(n) { emit('close'); if (n.to) router.push(n.to) }
const TONE = { missing: 'missing', anomaly: 'anomaly', expiring: 'expiring' }
</script>

<template>
  <Teleport to="body">
    <template v-if="open">
      <div class="dwbg" @click="emit('close')" />
      <aside class="drawer" role="dialog" aria-modal="true" aria-label="알림">
        <div class="dwhead">
          <div><h4>알림 {{ items.length }}</h4><span class="dwsub">미확인 · 분석 실패 · 접수 불가 · 발송 실패 · D-7 — 명세가 「담당자에게 알린다」고 한 것들</span></div>
          <button class="cl" aria-label="닫기" @click="emit('close')">✕</button>
        </div>
        <div class="dwlist">
          <button v-for="n in items" :key="n.id" class="nitem" @click="go(n)">
            <StatusChip :label="n.kind" :tone="TONE[n.tone] ?? 'processing'" flat />
            <span class="nbody"><b>{{ n.title }}</b><span>{{ n.sub }}</span></span>
            <time>{{ n.at }}</time>
          </button>
          <div v-if="!items.length" class="dwempty"><b>알릴 것이 없습니다</b><p>미확인 · 분석 실패 · 발송 실패 건이 생기면 여기에 뜹니다.</p></div>
        </div>
        <div v-if="loadedAt" class="dwfoot">{{ loadedAt.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) }} 기준 · 알림 API 가 없어 접수함·발송 이력에서 골라낸 것입니다</div>
      </aside>
    </template>
  </Teleport>
</template>
