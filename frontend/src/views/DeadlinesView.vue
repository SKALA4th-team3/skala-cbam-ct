<script setup>
import { computed, onMounted, ref } from 'vue'
import { Deadlines, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useUi } from '@/stores/ui'

const ui = useUi()
const months = ref([]); const rows = ref([])
onMounted(async () => {
  months.value = (await Deadlines.list()).months
  rows.value = allRows(await Deadlines.unsubmitted(), 'GET /submissions?status=NOT_SUBMITTED')
})
const picked = computed(() => rows.value.filter(r => r.checked))
async function send() {
  /* 명세 14번의 targets 는 {supplierId, partId} 객체 배열이다. id 배열을 넘기고 있었다.
     partId 는 목 데이터에 없다 — 없는 값을 지어내지 않고 비운 채 보낸다 (24번과 같은 규칙). */
  const targets = picked.value.map(r => ({ supplierId: r.id, partId: r.partId ?? null }))
  const ids = targets.map(t => t.supplierId)
  try {
    await Deadlines.remind(targets)
    rows.value = rows.value.map(r => ids.includes(r.id) ? { ...r, lastSent: '2026-09-03', checked: false } : r)
    ui.say(ids.length + '곳에 리마인드를 보냈습니다 · 이력은 협력사 상세에 남습니다')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
</script>

<template>
  <ViewHead api="UC-09 · 마감 · GET /submission-deadlines" back="관제" backTo="/">
    <template #title>9월 마감까지 <b>27일</b> 남았습니다.</template>
    <template #lede>마감일은 매월 말일로 고정입니다. D-7 부터 미제출 업체에 경보가 뜹니다.</template>
  </ViewHead>

  <div class="subhead stage" style="--d:100ms">
    <h3>마감 일정</h3><p>마감이 지난 달은 그때 확정된 값으로 남습니다.</p>
  </div>
  <div class="dltab stage" style="--d:140ms">
    <div class="h"><span>마감월</span><span>적격</span><span>부적격</span><span>미제출</span><span>남은 일수</span><span>상태</span></div>
    <div v-for="m in months" :key="m.month" class="dl" :class="{ now: m.now }">
      <b>{{ m.month }}</b>
      <span class="n ok">{{ m.ok }}</span><span class="n rj">{{ m.reject }}</span><span class="n ms">{{ m.missing }}</span>
      <span class="left">{{ m.left }}</span>
      <StatusChip :label="m.state" :tone="m.tone" />
    </div>
  </div>

  <div class="subhead stage" style="--d:180ms">
    <h3>미제출 {{ rows.length }}곳 · 리마인드 발송</h3>
    <p>메일에는 마감일과 제출 양식 안내가 들어갑니다. 발송 이력은 협력사 상세에 남습니다.</p>
  </div>
  <div class="rmlist stage" style="--d:220ms">
    <div class="h"><span></span><span>협력업체</span><span>담당자 이메일</span><span>마지막 발송</span><span>미제출 경과</span></div>
    <div v-for="r in rows" :key="r.id" v-clickable class="rm" :class="{ on: r.checked }"
         role="checkbox" :aria-checked="!!r.checked" :aria-label="`${r.name} 리마인드 대상`"
         @click="r.checked = !r.checked">
      <i class="ck"></i><b>{{ r.name }}</b>
      <span class="ml">{{ r.email }}</span><span class="lt">{{ r.lastSent }}</span>
      <span class="ov" :class="{ late: r.late }">{{ r.overdue }}</span>
    </div>
  </div>

  <ActionBar :title="picked.length ? `${picked.length}곳을 골랐습니다.` : '보낼 곳을 고르세요.'"
             note="D-7 부터는 고르지 않아도 매일 자동으로 나갑니다" delay="260ms">
    <button class="quiet" @click="ui.say('마감일 2026-09-30 과 제출 양식 안내가 들어간 기본 문안입니다')">발송 문안 미리보기</button>
    <button class="btn" :disabled="!picked.length" @click="send">
      {{ picked.length ? `${picked.length}곳에 리마인드 발송` : '리마인드 발송' }}
    </button>
  </ActionBar>
  <div class="spacer"></div>
</template>
