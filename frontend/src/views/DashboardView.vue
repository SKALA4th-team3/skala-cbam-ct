<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useBoard } from '@/stores/board'
import DonutChart from '@/components/DonutChart.vue'
import DeadlineRing from '@/components/DeadlineRing.vue'
import SparkBars from '@/components/SparkBars.vue'
import SubmissionStrip from '@/components/SubmissionStrip.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import CountUp from '@/components/CountUp.vue'
import RevealText from '@/components/RevealText.vue'
import MonthHeat from '@/components/MonthHeat.vue'
import EmissionBars from '@/components/EmissionBars.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import { isAlarming } from '@/composables/motion'
import { Review, allRows } from '@/api'

const board = useBoard()
const router = useRouter()
const tab = ref('t1')
const queue = ref([])
const loadingQueue = ref(true)

onMounted(async () => {
  await board.load()
  queue.value = allRows(await Review.queue({ status: '검토 대기' }), 'GET /submissions').filter(r => r.severity)
  loadingQueue.value = false
})

const S = computed(() => board.summary)
const j = computed(() => board.judged)
const parts = computed(() => [
  [j.value.적격, 'complete', '적격'], [j.value.부적격, 'reject', '부적격'], [j.value.미제출, 'missing', '미제출'],
])
const todo = computed(() => S.value?.todo ?? [])

/* 도넛 조각과 아래 범례가 같은 상태를 본다 — 어느 쪽에 손을 올려도 같이 밝아진다 */
const lit = ref(null)
const dDay = computed(() => S.value?.dDay ?? 0)
const leftPct = computed(() => Math.max(0, Math.min(100, dDay.value / 30 * 100)))
/* 지난달 대비 — 손으로 적지 않고 추이에서 뺀다. 내려갔으면 내려갔다고 말한다 */
const delta = computed(() => { const t = S.value?.trend ?? []; return t.length > 1 ? t.at(-1) - t.at(-2) : 0 })
const when = computed(() => S.value?.lastRecalcAt ? S.value.lastRecalcAt.slice(0, 16).replace('T', ' ') : '')

/* 40번 — 월별 판정 현황. 현재 달이 기본값이다 */
const pickedMonth = ref(null)
const monthPick = computed(() => {
  const list = S.value?.monthly ?? []
  return list.find(m => m.month === (pickedMonth.value ?? S.value?.month)) ?? list.at(-1) ?? null
})
const pct = (n, m) => { const t = m.적격 + m.부적격 + m.미제출; return t ? Math.round(n / t * 100) : 0 }

async function draftAll() {
  router.push('/feedback?bulk=1')
}
</script>

<template>
  <div class="brief stage" style="--d:0ms">
    <div class="when"><i></i>{{ board.recalculated ? '방금 재판정' : `마지막 재판정 ${when}` }}</div>
    <h1 v-if="S">
      <RevealText>미제출 <b><CountUp :value="j.미제출" suffix="곳" /></b>,
        <template v-if="S.longMissing">그중 <b>{{ S.longMissing }}곳</b>은 {{ S.longestRun }}개월째 답이 없습니다.</template>
        <template v-else>이번 달 새로 빠진 곳입니다.</template>
      </RevealText>
    </h1>
    <h1 v-else class="shim">집계를 세는 중입니다</h1>
    <p v-if="S">마감까지 <b><CountUp :value="dDay" suffix="일" /></b> 남았고, 협력 중인 {{ j.total }}곳 가운데 적격 {{ j.적격 }} · 부적격 {{ j.부적격 }} · 미제출 {{ j.미제출 }}입니다.</p>
    <div class="acts">
      <button class="quiet" :disabled="board.loading" @click="board.reload()">
        <span :class="{ shim: board.loading }">{{ board.loading ? '재판정 중' : '재판정' }}</span>
      </button>
      <button class="tactile sm" :disabled="!S?.draftable" @click="draftAll">
        <span class="plate"></span><span class="cap">{{ S?.draftable ? `초안 ${S.draftable}건 일괄 생성` : '만들 초안이 없습니다' }}</span>
      </button>
    </div>
  </div>

  <div class="readouts stage" style="--d:120ms">
    <div class="ro">
      <div class="cap">이번 달 제출 현황</div>
      <div class="fig"><DonutChart :parts="parts" :total="j.total" :lit="lit" @lit="lit = $event" /></div>
      <div>
        <div class="val"><CountUp :value="j.적격" /><small>/ {{ j.total }} 적격 (<CountUp :value="board.okRate" />%)</small></div>
        <div class="stackleg">
          <span v-for="p in parts" :key="p[2]" :class="{ lit: lit === p[1], dim: lit && lit !== p[1] }"
                @mouseenter="lit = p[1]" @mouseleave="lit = null">
            <i :style="{ background: `var(--${p[1]})` }"></i>{{ p[2] }} {{ p[0] }}
          </span>
        </div>
        <div class="note" style="margin-top:8px">
          검토 대기 심각도 HIGH {{ S?.severity.HIGH ?? 0 }} · MEDIUM {{ S?.severity.MEDIUM ?? 0 }} · LOW {{ S?.severity.LOW ?? 0 }}
        </div>
        <SparkBars v-if="S" :values="S.trend" />
        <div class="note">최근 6개월 적격 추이 ·
          <span class="delta" :class="delta >= 0 ? 'up' : 'down'">{{ delta >= 0 ? '▲' : '▼' }} {{ Math.abs(delta) }}</span> 지난달 대비
        </div>
      </div>
    </div>
    <div v-clickable class="ro link" :aria-label="`${S?.month ?? ''} 마감 상세`" @click="router.push('/deadlines')">
      <div class="cap">{{ S ? Number(S.month.slice(5)) + '월' : '' }} 마감</div>
      <div class="fig"><DeadlineRing :days="dDay" :percent="leftPct" /></div>
      <div>
        <div class="val" style="font-size:15px;font-weight:500">{{ S?.deadline ?? '' }}</div>
        <div class="note">미제출 {{ j.미제출 }}곳 · 붉은 구간이 D-7 경보</div>
      </div>
    </div>
  </div>

  <!-- 40번 판정 현황 — 월별 비율과 심각도별 건수. 현재 달이 기본 선택 -->
  <div v-if="S" class="judge stage" style="--d:180ms">
    <div class="jg-l">
      <div class="cap">월별 판정 현황 · 최근 12개월</div>
      <MonthHeat :months="S.monthly" :picked="monthPick?.month" @pick="pickedMonth = $event" />
    </div>
    <div v-if="monthPick" class="jg-r">
      <div class="cap">{{ monthPick.month }}{{ monthPick.month === S.month ? ' · 이번 달' : '' }}</div>
      <div class="jg-ratio">
        <span><i style="background:var(--complete)"></i>적격 <b>{{ pct(monthPick.적격, monthPick) }}%</b><small>{{ monthPick.적격 }}</small></span>
        <span><i style="background:var(--reject)"></i>부적격 <b>{{ pct(monthPick.부적격, monthPick) }}%</b><small>{{ monthPick.부적격 }}</small></span>
        <span><i style="background:var(--missing)"></i>미제출 <b>{{ pct(monthPick.미제출, monthPick) }}%</b><small>{{ monthPick.미제출 }}</small></span>
      </div>
      <div class="note">심각도별 건수는 이번 달 검토 대기 기준입니다 — 지난달은 확정된 값으로 남아 심각도가 없습니다</div>
    </div>
  </div>

  <!-- 41번 배출량 집계 — 확정 데이터 기준 완제품별 합계. 미확정 부품이 포함된 완제품은 따로 표시 -->
  <div v-if="S" class="emis stage" style="--d:220ms">
    <div class="subhead"><h3>완제품별 내재배출량 · 확정 기준</h3><p>빗금으로 열린 막대는 미확정 부품이 있어 합계를 낼 수 없는 제품입니다. 값이 없다는 뜻이지 0이 아닙니다.</p></div>
    <EmissionBars :rows="S.emissions" />
  </div>

  <nav class="tabs stage" style="--d:260ms" aria-label="상세 보기">
    <button :class="{ on: tab === 't1' }" @click="tab = 't1'">손봐야 할 곳 <b>{{ S?.todoTotal ?? 0 }}</b></button>
    <button :class="{ on: tab === 't2' }" @click="tab = 't2'">검토 대기 <b>{{ queue.length }}</b></button>
    <button :class="{ on: tab === 't3' }" @click="tab = 't3'">오늘 수신 <b>{{ S?.inboxToday ?? 0 }}</b></button>
  </nav>

  <div v-if="tab === 't1'" class="list" v-reveal>
    <SkeletonRows v-if="!S" :rows="4" />
    <div v-for="s in todo" :key="s.id" v-clickable class="row link"
         :aria-label="`${s.name} 상세`" @click="router.push(`/suppliers/${s.id}`)">
      <!-- 맥동은 3개월 이상 연속 미제출에만 붙는다 (composables/motion.js · isAlarming) -->
      <div class="n" :class="{ beat: isAlarming(s) }"><b>{{ s.name }}</b><span>{{ [s.item, s.why].filter(Boolean).join(' · ') }}</span></div>
      <SubmissionStrip :pattern="s.strip" axis />
      <StatusBadge :value="s.judgement" />
    </div>
  </div>
  <div v-else-if="tab === 't2'" class="list" v-reveal>
    <SkeletonRows v-if="loadingQueue" :rows="4" />
    <div v-for="r in queue" :key="r.id" v-clickable class="row al link"
         :aria-label="`${r.supplier} 검토`" @click="router.push(`/review/${r.id}`)">
      <div class="n"><b>{{ r.supplier }}</b><span>{{ r.item }}</span></div>
      <span class="rule">{{ r.rule }}</span><span class="why">{{ r.why }}</span>
      <StatusBadge :value="r.severity" />
    </div>
  </div>
  <div v-else class="list" v-reveal>
    <div v-clickable class="row link" aria-label="접수함 열기" @click="router.push('/inbox')">
      <div class="n"><b>오늘 {{ S?.inboxToday ?? 0 }}건이 들어왔습니다</b>
        <span>{{ S?.unidentified ? `미확인 ${S.unidentified}건은 협력업체를 지정해야 분석이 돕니다` : '접수함에서 원문과 첨부를 확인합니다' }}</span></div>
    </div>
  </div>

  <div class="after">
    <a href="#" @click.prevent="router.push('/suppliers?judgement=' + encodeURIComponent('미제출,부적격'))">손봐야 할 곳 {{ S?.todoTotal ?? 0 }}곳 전체 보기
      <svg class="i" viewBox="0 0 24 24" style="width:14px;height:14px"><path d="M9 6l6 6-6 6" /></svg></a>
    <span class="legend"><i></i>부적격 <i class="s2"></i>미제출 · 최근 12개월</span>
  </div>
  <div class="spacer"></div>
</template>
