<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Suppliers, allRows } from '@/api'
import { useTable } from '@/composables/useTable'
import { useFlip } from '@/composables/useFlip'
import { isAlarming } from '@/composables/motion'
import DataToolbar from '@/components/DataToolbar.vue'
import ViewHead from '@/components/ViewHead.vue'
import SubmissionStrip from '@/components/SubmissionStrip.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const rows = ref([])
onMounted(async () => { rows.value = allRows(await Suppliers.list({ size: 1000 }), 'GET /suppliers') })

const facets = [
  { key: 'country', label: '국가', field: 'country' },
  { key: 'tie', label: '거래 상태', field: 'tie' },
  { key: 'judgement', label: '판정 결과', field: 'judgement' },
]
/* 기본 정렬은 업체명순(`companyName`) — ADR-0007.
   심각도 정렬은 아직 없다. 심각도는 협력업체의 속성이 아니라 제출 데이터에서 나온다 —
   36번이 규칙 R1~R7 로 HIGH·MEDIUM·LOW 를 부여한 뒤에 붙인다.
   그전까지 아래 「판정 결과순」은 판정값 순서일 뿐 심각도가 아니다. 이름을 그렇게 쓴다. */
const RANK = { 미제출: 0, 부적격: 1, '검토 대기': 2, 적격: 3 }
const sorts = [
  { key: 'name', label: '업체명순', fn: (a, b) => a.name.localeCompare(b.name) },
  { key: 'judgement', label: '판정 결과순', fn: (a, b) => RANK[a.judgement] - RANK[b.judgement] },
]
const t = useTable(rows, { search: 'name', facets, sorts })

/* 필터를 켜면 남는 행이 제자리를 찾아간다 — 무엇이 걸러졌는지 보이게 (GSAP Flip 과 같은 원리) */
const list = ref(null)
useFlip(list, () => t.filtered)
/* 맥동을 붙일 곳. 한 화면에 최대 두 곳만 — 전부 움직이면 아무것도 안 보인다 */
const beating = computed(() => t.filtered.filter(isAlarming).slice(0, 2).map(s => s.id))
</script>

<template>
  <ViewHead kicker="대한민국 · 경남 · 경북 · 전남">
    <template #title>협력사 {{ rows.length }}곳</template>
    <template #lede>철강 32 · 알루미늄 11 · 기타 5 · 이번 달 마감 2026-09-30</template>
    <template #acts>
      <button class="quiet" @click="router.push('/suppliers/new')">
        <svg class="i" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></svg>협력사 등록
      </button>
    </template>
  </ViewHead>

  <DataToolbar :table="t" :facets="facets" :sorts="sorts" placeholder="협력업체명 검색" unit="곳" />

  <div class="list" ref="list" v-reveal>
    <div v-for="s in t.filtered" :key="s.id" :data-flip="s.id" v-clickable class="row link"
         :aria-label="`${s.name} 상세`" @click="router.push(`/suppliers/${s.id}`)">
      <!-- 도시·품목은 명세 №3 응답에 없다. 실서버에서는 비어 오므로 「 · 」만 남지 않게 거른다 -->
      <!-- 맥동은 3개월 이상 연속 미제출인 곳에만, 한 화면 최대 두 곳 (composables/motion.js) -->
      <div class="n" :class="{ beat: beating.includes(s.id) }">
        <b>{{ s.name }}</b><span>{{ [s.city, s.item].filter(Boolean).join(' · ') }}</span>
      </div>
      <SubmissionStrip :pattern="s.strip" axis />
      <StatusBadge :value="s.judgement" />
    </div>
    <EmptyState v-if="!t.filtered.length"
      title="조건에 맞는 항목이 없습니다."
      note="데이터가 없는 게 아니라 필터에 걸린 것이 없다는 뜻입니다."
      action="필터 해제" @action="t.clearAll()" />
  </div>

  <div class="after">
    <span class="legend"><i></i>부적격 <i class="s2"></i>미제출 · 최근 12개월</span>
  </div>
  <div class="spacer"></div>
</template>
