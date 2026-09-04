<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Deadlines, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import SubTabs from '@/components/SubTabs.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import ModalBox from '@/components/ModalBox.vue'
import { useUi } from '@/stores/ui'

const ui = useUi(); const router = useRouter()
const months = ref([]); const rows = ref([]); const loading = ref(true)
async function load() {
  months.value = (await Deadlines.list()).months
  rows.value = allRows(await Deadlines.unsubmitted(), 'GET /submissions?status=NOT_SUBMITTED')
  loading.value = false
}
onMounted(load)
const now = computed(() => months.value.find(m => m.now))
const picked = computed(() => rows.value.filter(r => r.checked))
const preview = ref(false)
const busy = ref(false)
/* 17번 — 미제출 업체를 골라 리마인드 발송. 마감일·제출 양식 안내 포함. 이력은 협력사 상세에 남는다 */
async function send() {
  const targets = picked.value.map(r => ({ supplierId: r.id, partId: r.partId ?? null }))
  busy.value = true
  try {
    const r = await Deadlines.remind(targets)
    ui.say(`${r.targetCount}곳에 리마인드를 보냈습니다 · 이력은 협력사 상세에 남습니다`)
    await load()
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
const allOn = computed(() => rows.value.length && rows.value.every(r => r.checked))
const toggleAll = () => { const v = !allOn.value; rows.value.forEach(r => (r.checked = v)) }
</script>

<template>
  <SubTabs :tabs="[{ label: '요약', to: '/' }, { label: '마감', to: '/deadlines', count: now ? `D-${now.dDay}` : null }]" />
  <ViewHead api="UC-09 · 마감 · GET /submission-deadlines" back="관제" backTo="/">
    <template #title><template v-if="now">{{ Number(now.month.slice(5)) }}월 마감까지 <b :class="{ warn: now.alarm }">{{ now.dDay }}일</b> 남았습니다.</template><span v-else class="shim">마감을 세는 중</span></template>
    <template #lede>마감일은 매월 말일로 고정입니다. D-7 부터 미제출 업체에 경보가 뜹니다 (16번). 협력끊김 업체는 대상에서 빠집니다 (6번).</template>
  </ViewHead>

  <div class="subhead stage" style="--d:100ms"><h3>마감 일정</h3><p>마감이 지난 달은 그때 확정된 값으로 남습니다.</p></div>
  <div class="dltab stage" style="--d:140ms">
    <div class="h"><span>마감월</span><span>적격</span><span>부적격</span><span>미제출</span><span>남은 일수</span><span>상태</span></div>
    <SkeletonRows v-if="loading" :rows="3" :cols="['1fr', '90px', '90px', '90px', '120px', '100px']" />
    <div v-for="m in months" :key="m.month" class="dl" :class="{ now: m.now }">
      <b>{{ m.deadline }}</b>
      <span class="n ok">{{ m.ok }}</span><span class="n rj">{{ m.reject }}</span><span class="n ms">{{ m.missing }}</span>
      <span class="left">{{ m.left }}</span>
      <StatusChip :label="m.state" :tone="m.tone" />
    </div>
  </div>

  <div class="subhead stage" style="--d:180ms">
    <h3>미제출 {{ rows.length }}곳 · 리마인드 발송</h3>
    <p>메일에는 마감일과 제출 양식 안내가 들어갑니다. 발송 이력은 협력사 상세에 남습니다 (17번).</p>
  </div>
  <div class="rmlist stage" style="--d:220ms">
    <div class="h"><span><i class="ck" :class="{ on: allOn }" role="checkbox" :aria-checked="!!allOn" tabindex="0" aria-label="전체 선택" @click="toggleAll" @keydown.enter.space.prevent="toggleAll"></i></span><span>협력업체</span><span>담당자 이메일</span><span>마지막 발송</span><span>미제출 경과</span></div>
    <div v-for="r in rows" :key="r.id" v-clickable class="rm" :class="{ on: r.checked }"
         role="checkbox" :aria-checked="!!r.checked" :aria-label="`${r.name} 리마인드 대상`" @click="r.checked = !r.checked">
      <i class="ck"></i><b>{{ r.name }}<a href="#" class="lnk" @click.prevent.stop="router.push(`/suppliers/${r.id}`)">상세</a></b>
      <span class="ml">{{ r.email }}</span><span class="lt">{{ r.lastSent ?? '보낸 적 없음' }}</span>
      <span class="ov" :class="{ late: r.late }">{{ r.overdue }}</span>
    </div>
    <div v-if="!loading && !rows.length" class="noresult"><b>미제출 업체가 없습니다.</b><p>이번 달 협력 중인 업체가 전부 제출했습니다.</p></div>
  </div>

  <ActionBar :title="picked.length ? `${picked.length}곳을 골랐습니다.` : '보낼 곳을 고르세요.'"
             note="D-7 부터는 고르지 않아도 매일 자동으로 나갑니다" delay="260ms">
    <button class="quiet" @click="preview = true">발송 문안 미리보기</button>
    <button class="btn" :disabled="!picked.length || busy" @click="send">{{ picked.length ? `${picked.length}곳에 리마인드 발송` : '리마인드 발송' }}</button>
  </ActionBar>

  <ModalBox :open="preview" title="리마인드 문안" :sub="`마감일 ${now?.deadline ?? ''} 과 제출 양식 안내가 들어갑니다 (17번)`" @close="preview = false">
    <div class="letter">
      <p>{담당자명} 님께</p>
      <p>{{ now ? Number(now.month.slice(5)) : '' }}월분 탄소 배출 데이터 제출 마감이 <b>{{ now?.deadline }}</b> 입니다. 아직 제출 자료를 받지 못했습니다.</p>
      <p class="list">- 제출 양식: 기존 엑셀 양식(품명 · 생산량 · 직접 배출량 · 간접 배출량 · 생산국 · 제출 월)
- 보내실 곳: 이 메일로 회신 (xlsx · csv · pdf)</p>
      <p>마감 후 접수된 자료는 다음 달 판정에 반영됩니다.</p>
      <p>CBAM CT</p>
    </div>
    <template #acts><button class="btn" @click="preview = false">닫기</button></template>
  </ModalBox>
  <div class="spacer"></div>
</template>
