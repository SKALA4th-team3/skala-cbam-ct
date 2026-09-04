<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Review, allRows } from '@/api'
import { useTable } from '@/composables/useTable'
import { useFlip } from '@/composables/useFlip'
import DataToolbar from '@/components/DataToolbar.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import StatusChip from '@/components/StatusChip.vue'
import EmptyState from '@/components/EmptyState.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import ViewHead from '@/components/ViewHead.vue'

const router = useRouter()
const items = ref([]); const loading = ref(true)
onMounted(async () => {
  /* 29번 — 처리한 건은 목록에서 지우지 않고 resolved_at 과 함께 남긴다 */
  items.value = allRows(await Review.queue(), 'GET /submissions')
  loading.value = false
})

/* 29번 — 「협력업체·제출일·판정 결과·심각도로 조회. 기본 정렬은 심각도 높은 순」 */
const facets = [
  { key: 'supplier', label: '협력업체', field: 'supplier' },
  { key: 'judgement', label: '판정 결과', field: 'judgement' },
  { key: 'severity', label: '심각도', field: 'severity' },
  { key: 'status', label: '처리 상태', field: 'status' },
]
const RANK = { HIGH: 0, MEDIUM: 1, LOW: 2 }
const sorts = [
  { key: 'severity', label: '심각도 높은 순', fn: (a, b) => (RANK[a.severity] ?? 9) - (RANK[b.severity] ?? 9) || (b.submittedAt ?? '').localeCompare(a.submittedAt ?? '') },
  { key: 'submitted', label: '제출일 최신순', fn: (a, b) => (b.submittedAt ?? '').localeCompare(a.submittedAt ?? '') },
  { key: 'supplier', label: '협력업체명순', fn: (a, b) => a.supplier.localeCompare(b.supplier) },
]
const t = useTable(items, { search: 'supplier', facets, sorts, sync: true })
const list = ref(null)
useFlip(list, () => t.filtered)
const day = s => s ? s.slice(5, 10).replace('-', '.') : '—'
const STATUS_TONE = { '검토 대기': 'processing', 확정: 'complete', 반려: 'reject' }
</script>

<template>
  <ViewHead api="UC-07 · 데이터 검토 · GET /submissions?status=REVIEW_PENDING">
    <template #title>검토 대기 {{ items.filter(i => i.status === '검토 대기').length }}건이 심각도 순으로 서 있습니다.</template>
    <template #lede>판정 사유(R 코드)와 심각도를 함께 보여줍니다. 처리한 건은 목록에서 지우지 않고 처리 시각과 함께 남깁니다.</template>
  </ViewHead>

  <DataToolbar :table="t" :facets="facets" :sorts="sorts" placeholder="협력업체명 검색" unit="건" />

  <div class="alerts" ref="list" v-reveal>
    <SkeletonRows v-if="loading" :rows="5" :cols="['78px', '1fr', '210px', '92px']" />
    <div v-for="r in t.filtered" :key="r.id" :data-flip="r.id" v-clickable class="at link" :class="{ done: r.status !== '검토 대기' }"
         :aria-label="`${r.supplier} 검토`" @click="router.push(`/review/${r.id}`)">
      <span class="rule">{{ r.rule ?? '—' }}<em class="day">{{ day(r.submittedAt) }}</em></span>
      <div><b>{{ r.supplier }}</b><span class="sub">{{ r.item }} · 판정 {{ r.judgement }}</span></div>
      <span class="why">{{ r.why }}</span>
      <StatusBadge v-if="r.severity" :value="r.severity" />
      <StatusChip v-else label="규칙 없음" tone="complete" flat />
      <StatusChip :label="r.status" :tone="STATUS_TONE[r.status] ?? 'processing'" />
    </div>
    <EmptyState v-if="!loading && !t.filtered.length" title="조건에 맞는 건이 없습니다."
      note="데이터가 없는 게 아니라 필터에 걸린 것이 없다는 뜻입니다." action="필터 해제" @action="t.clearAll()" />
  </div>
  <div class="after"><a href="#" @click.prevent="router.push('/settings')">판정 임계값 바꾸기</a></div>
  <div class="spacer"></div>
</template>
