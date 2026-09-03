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
import { isAlarming } from '@/composables/motion'
import { Review, allRows } from '@/api'

const board = useBoard()
const router = useRouter()
const tab = ref('t1')
const queue = ref([])

onMounted(async () => {
  await board.load()
  queue.value = allRows(await Review.queue(), 'GET /submissions')
})

const j = computed(() => board.judged)
const parts = computed(() => [
  [j.value.적격, 'complete', '적격'], [j.value.부적격, 'reject', '부적격'], [j.value.미제출, 'missing', '미제출'],
])
const todo = computed(() => board.summary?.todo ?? [])

/* 도넛 조각과 아래 범례가 같은 상태를 본다 — 어느 쪽에 손을 올려도 같이 밝아진다 */
const lit = ref(null)
/* D-일수는 마감일에서 센다. 링의 남은 비율도 같은 값에서 나온다 */
const dDay = computed(() => board.summary?.dDay ?? 27)
const leftPct = computed(() => Math.max(0, Math.min(100, dDay.value / 30 * 100)))
</script>

<template>
  <div class="brief stage" style="--d:0ms">
    <div class="when"><i></i>{{ board.recalculated ? '방금 재판정 · 경보 2건 자동 해소' : '마지막 재판정 9분 전 · 2026-09-02' }}</div>
    <h1>
      <RevealText>미제출 <b><CountUp :value="j.미제출" suffix="곳" /></b>, 그중 <b>2곳</b>은 5개월째 답이 없습니다.</RevealText>
    </h1>
    <p>마감까지 <b><CountUp :value="dDay" suffix="일" /></b> 남았고, 미제출 {{ j.미제출 }}곳 중 2곳이 다섯 달째 회신이 없습니다.</p>
    <div class="acts">
      <button class="quiet" :disabled="board.loading" @click="board.reload()">
        <span :class="{ shim: board.loading }">{{ board.loading ? '재판정 중' : '재판정' }}</span>
      </button>
      <button class="tactile sm" @click="router.push('/feedback')">
        <span class="plate"></span><span class="cap">초안 17건 일괄 생성</span>
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
          부적격 심각도 HIGH {{ board.summary?.severity.HIGH }} · MEDIUM {{ board.summary?.severity.MEDIUM }} · LOW {{ board.summary?.severity.LOW }}
        </div>
        <SparkBars v-if="board.summary" :values="board.summary.trend" />
        <div class="note">최근 6개월 추이 · <span class="delta up">▲ {{ board.recalculated ? 3 : 2 }}</span> 지난달 대비</div>
      </div>
    </div>
    <div v-clickable class="ro link" aria-label="9월 마감 상세" @click="router.push('/deadlines')">
      <div class="cap">9월 마감</div>
      <div class="fig"><DeadlineRing :days="dDay" :percent="leftPct" /></div>
      <div>
        <div class="val" style="font-size:15px;font-weight:500">{{ board.summary?.deadline ?? '2026-09-30' }}</div>
        <div class="note">미제출 {{ j.미제출 }}곳 · 붉은 구간이 D-7 경보</div>
      </div>
    </div>
  </div>

  <nav class="tabs stage" style="--d:220ms" aria-label="상세 보기">
    <button :class="{ on: tab === 't1' }" @click="tab = 't1'">손봐야 할 곳 <b>{{ todo.length }}</b></button>
    <button :class="{ on: tab === 't2' }" @click="tab = 't2'">판정 결과 <b>{{ j.부적격 }}</b></button>
    <button :class="{ on: tab === 't3' }" @click="tab = 't3'">최근 수신</button>
  </nav>

  <div v-if="tab === 't1'" class="list" v-reveal>
    <div v-for="s in todo" :key="s.id" v-clickable class="row link"
         :aria-label="`${s.name} 상세`" @click="router.push(`/suppliers/${s.id}`)">
      <!-- 맥동은 3개월 이상 연속 미제출에만 붙는다 (composables/motion.js · isAlarming) -->
      <div class="n" :class="{ beat: isAlarming(s) }"><b>{{ s.name }}</b><span>{{ s.item }} · {{ s.why }}</span></div>
      <SubmissionStrip :pattern="s.strip" axis />
      <StatusBadge :value="s.judgement" />
    </div>
  </div>
  <div v-else-if="tab === 't2'" class="list" v-reveal>
    <div v-for="r in queue" :key="r.id" v-clickable class="row al link"
         :aria-label="`${r.supplier} 검토`" @click="router.push(`/review/${r.id}`)">
      <div class="n"><b>{{ r.supplier }}</b><span>{{ r.item }}</span></div>
      <span class="rule">{{ r.rule }}</span><span class="why">{{ r.why }}</span>
      <StatusBadge :value="r.severity" />
    </div>
  </div>
  <div v-else class="list" v-reveal>
    <div v-clickable class="row link" aria-label="접수함 열기" @click="router.push('/inbox')">
      <div class="n"><b>오늘 4건이 들어왔습니다</b><span>접수함에서 원문과 첨부를 확인합니다</span></div>
    </div>
  </div>

  <div class="after">
    <a href="#" @click.prevent="router.push('/suppliers')">손봐야 할 곳 17곳 전체 보기
      <svg class="i" viewBox="0 0 24 24" style="width:14px;height:14px"><path d="M9 6l6 6-6 6" /></svg></a>
    <span class="legend"><i></i>부적격 <i class="s2"></i>미제출 · 최근 12개월</span>
  </div>
  <div class="spacer"></div>
</template>
