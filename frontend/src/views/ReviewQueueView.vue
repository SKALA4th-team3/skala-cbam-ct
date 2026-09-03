<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Review } from '@/api'
import ViewHead from '@/components/ViewHead.vue'

const router = useRouter()
const items = ref([])
const filter = ref('전체')
const FILTERS = ['전체', 'HIGH', 'MEDIUM', 'LOW']
onMounted(async () => { items.value = (await Review.queue()).items })
const shown = () => filter.value === '전체' ? items.value : items.value.filter(i => i.severity === filter.value)
</script>

<template>
  <ViewHead api="UC-07 · 데이터 검토 · GET /submissions?status=review">
    <template #title>검토 대기 {{ items.length }}건이 심각도 순으로 서 있습니다.</template>
    <template #lede>판정 사유(R 코드)와 심각도를 함께 보여줍니다. 처리한 건은 목록에서 지우지 않고 resolved_at 과 함께 남깁니다.</template>
  </ViewHead>

  <div class="filters stage" style="--d:100ms">
    <button v-for="f in FILTERS" :key="f" :class="{ on: filter === f }" @click="filter = f">
      {{ f }} <b>{{ f === '전체' ? items.length : items.filter(i => i.severity === f).length }}</b>
    </button>
  </div>

  <div class="alerts stage" style="--d:160ms">
    <div v-for="r in shown()" :key="r.id" class="at link" @click="router.push(`/review/${r.id}`)">
      <span class="rule">{{ r.rule }}</span>
      <div><b>{{ r.supplier }}</b><span class="sub">{{ r.item }}</span></div>
      <span class="why">{{ r.why }}</span>
      <span class="badge" :class="r.severity === 'HIGH' ? 'missing' : r.severity === 'MEDIUM' ? 'anomaly' : 'expiring'">{{ r.severity }}</span>
      <span class="ago">검토</span>
    </div>
  </div>
  <div class="after"><a href="#" @click.prevent="router.push('/settings')">판정 임계값 바꾸기</a></div>
  <div class="spacer"></div>
</template>
