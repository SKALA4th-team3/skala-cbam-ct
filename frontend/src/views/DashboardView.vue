<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useBoard } from '@/stores/board'
import DonutChart from '@/components/DonutChart.vue'
import DeadlineRing from '@/components/DeadlineRing.vue'
import SparkBars from '@/components/SparkBars.vue'
import SubmissionStrip from '@/components/SubmissionStrip.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { Review } from '@/api'

const board = useBoard()
const router = useRouter()
const tab = ref('t1')
const queue = ref([])

onMounted(async () => {
  await board.load()
  queue.value = (await Review.queue()).items
})

const j = computed(() => board.judged)
const parts = computed(() => [
  [j.value.적격, 'complete', '적격'], [j.value.부적격, 'reject', '부적격'], [j.value.미제출, 'missing', '미제출'],
])
const todo = computed(() => board.summary?.todo ?? [])
</script>

<template>
  <div class="brief stage" style="--d:0ms">
    <div class="when"><i></i>{{ board.recalculated ? '방금 재판정 · 경보 2건 자동 해소' : '마지막 재판정 9분 전 · 2026-09-02' }}</div>
    <h1>미제출 <b>{{ j.미제출 }}곳</b>, 그중 <b>2곳</b>은 5개월째 답이 없습니다.</h1>
    <p>마감까지 <b>27일</b> 남았고, 미제출 {{ j.미제출 }}곳 중 2곳이 다섯 달째 회신이 없습니다.</p>
    <div class="acts">
      <button class="quiet" @click="board.load()">재판정</button>
      <button class="tactile sm" @click="router.push('/feedback')">
        <span class="plate"></span><span class="cap">초안 17건 일괄 생성</span>
      </button>
    </div>
  </div>

  <div class="readouts stage" style="--d:120ms">
    <div class="ro">
      <div class="cap">이번 달 제출 현황</div>
      <div class="fig"><DonutChart :parts="parts" :total="j.total" /></div>
      <div>
        <div class="val">{{ j.적격 }}<small>/ {{ j.total }} 적격 ({{ board.okRate }}%)</small></div>
        <div class="stackleg">
          <span v-for="p in parts" :key="p[2]"><i :style="{ background: `var(--${p[1]})` }"></i>{{ p[2] }} {{ p[0] }}</span>
        </div>
        <div class="note" style="margin-top:8px">
          부적격 심각도 HIGH {{ board.summary?.severity.HIGH }} · MEDIUM {{ board.summary?.severity.MEDIUM }} · LOW {{ board.summary?.severity.LOW }}
        </div>
        <SparkBars v-if="board.summary" :values="board.summary.trend" />
        <div class="note">최근 6개월 추이 · <span class="delta up">▲ {{ board.recalculated ? 3 : 2 }}</span> 지난달 대비</div>
      </div>
    </div>
    <div class="ro link" @click="router.push('/deadlines')">
      <div class="cap">9월 마감</div>
      <div class="fig"><DeadlineRing label="D-27" /></div>
      <div>
        <div class="val" style="font-size:15px;font-weight:500">2026-09-30</div>
        <div class="note">미제출 {{ j.미제출 }}곳 · D-7 부터 경보</div>
      </div>
    </div>
  </div>

  <nav class="tabs stage" style="--d:220ms" aria-label="상세 보기">
    <button :class="{ on: tab === 't1' }" @click="tab = 't1'">손봐야 할 곳 <b>{{ todo.length }}</b></button>
    <button :class="{ on: tab === 't2' }" @click="tab = 't2'">판정 결과 <b>{{ j.부적격 }}</b></button>
    <button :class="{ on: tab === 't3' }" @click="tab = 't3'">최근 수신</button>
  </nav>

  <div v-if="tab === 't1'" class="list stage" style="--d:280ms">
    <div v-for="s in todo" :key="s.id" class="row link" @click="router.push(`/suppliers/${s.id}`)">
      <div class="n"><b>{{ s.name }}</b><span>{{ s.item }} · {{ s.why }}</span></div>
      <SubmissionStrip :pattern="s.strip" />
      <StatusBadge :value="s.judgement" />
    </div>
  </div>
  <div v-else-if="tab === 't2'" class="list stage" style="--d:280ms">
    <div v-for="r in queue" :key="r.id" class="row al link" @click="router.push('/review')">
      <div class="n"><b>{{ r.supplier }}</b><span>{{ r.item }}</span></div>
      <span class="rule">{{ r.rule }}</span><span class="why">{{ r.why }}</span>
      <span class="badge" :class="r.severity === 'HIGH' ? 'missing' : r.severity === 'MEDIUM' ? 'anomaly' : 'expiring'">{{ r.severity }}</span>
    </div>
  </div>
  <div v-else class="list stage" style="--d:280ms">
    <div class="row link" @click="router.push('/inbox')">
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
