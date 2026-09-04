<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Products, Parts, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import { useUi } from '@/stores/ui'

const route = useRoute(); const router = useRouter(); const ui = useUi()
const p = ref(null); const missing = ref(null); const parts = ref([])
/* 13번 — 연간 수출량 · EU 회원국 · 부품 세부를 수정한다. 화면에서 고치고 «저장»할 때 한 번 보낸다 */
const form = ref({ euCountry: '', tons: '', bom: [] })
const EU = ['네덜란드', '독일', '벨기에', '프랑스', '이탈리아', '스페인', '폴란드', '체코', '오스트리아', '스웨덴']
onMounted(async () => {
  try { p.value = await Products.get(route.params.id) } catch (e) { missing.value = e; return }
  parts.value = allRows(await Parts.list({ size: 1000 }), 'GET /parts')
  form.value = { euCountry: p.value.euCountry, tons: String(p.value.tons), bom: p.value.parts.map(r => ({ part: r.part, input: String(r.input) })) }
})
const partOf = name => parts.value.find(x => x.name === name)
const stateOf = b => { const x = partOf(b.part); return !x ? '미등록' : x.factor ? '확정' : '미확정' }
const dirty = computed(() => p.value && JSON.stringify(form.value) !== JSON.stringify({ euCountry: p.value.euCountry, tons: String(p.value.tons), bom: p.value.parts.map(r => ({ part: r.part, input: String(r.input) })) }))
const valid = computed(() => Number(form.value.tons) > 0 && form.value.bom.length && form.value.bom.every(b => b.part && Number(b.input) > 0))
const pendingCount = computed(() => form.value.bom.filter(b => stateOf(b) !== '확정').length)
const busy = ref(false)
async function save() {
  busy.value = true
  try {
    p.value = await Products.update(p.value.id, { euCountry: form.value.euCountry, tons: Number(form.value.tons), bom: form.value.bom.map(b => ({ part: b.part, input: Number(b.input) })) })
    form.value = { euCountry: p.value.euCountry, tons: String(p.value.tons), bom: p.value.parts.map(r => ({ part: r.part, input: String(r.input) })) }
    ui.say(p.value.reportable ? '저장했습니다 · 부품이 전부 확정이라 신고할 수 있습니다' : `저장했습니다 · 미확정 부품 ${p.value.pendingCount}개가 남아 아직 신고할 수 없습니다`)
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
</script>

<template>
  <ViewHead v-if="missing" kicker="없는 완제품" back="제품" backTo="/products">
    <template #title>없는 완제품입니다.</template>
    <template #lede>{{ missing.message }} — <code>{{ route.params.id }}</code></template>
  </ViewHead>
  <SkeletonRows v-else-if="!p" :rows="4" />
  <template v-else>
    <ViewHead api="UC-03 · 완제품 · PATCH /products/{productId}" back="제품" backTo="/products">
      <template #title>{{ p.name }} 의 부품 구성</template>
      <template #lede>부품 세부는 부품명 · 협력사 · 투입량(t/t) · 상태로 이뤄집니다. 누락 여부와 상관없이 모든 협력사를 고를 수 있습니다 (12·13번).</template>
      <template #acts>
        <button class="quiet sm" @click="router.push(`/products/${p.id}/report`)">신고 가능 여부</button>
      </template>
    </ViewHead>

    <div class="form stage" style="--d:120ms">
      <label class="fld"><span>수출 대상 EU 회원국</span>
        <select v-model="form.euCountry"><option v-for="c in EU" :key="c" :value="c">{{ c }}</option></select></label>
      <label class="fld" :class="{ bad: !(Number(form.tons) > 0) }"><span>연간 수출량 (t)</span>
        <input v-model="form.tons" inputmode="decimal" /></label>
    </div>

    <div class="bom bomedit stage" style="--d:160ms">
      <div class="h"><span>부품명</span><span>공급 협력사</span><span>투입량 t/t</span><span>상태</span><span>벤치마크 팩터</span></div>
      <div v-for="(b, i) in form.bom" :key="i" class="bt">
        <select v-model="b.part" class="cellsel">
          <option value="" disabled>부품을 고르세요</option>
          <option v-for="x in parts" :key="x.id" :value="x.name">{{ x.name }} — {{ x.supplier }}</option>
        </select>
        <div class="cell">{{ partOf(b.part)?.supplier ?? '—' }}</div>
        <input v-model="b.input" class="cellin" inputmode="decimal" />
        <StatusChip :label="stateOf(b)" :tone="stateOf(b) === '확정' ? 'complete' : 'missing'" />
        <div class="cell mono">{{ partOf(b.part)?.factor ?? '미등록' }}
          <button class="cl sm" :disabled="form.bom.length === 1" aria-label="행 삭제" @click="form.bom.splice(i, 1)">✕</button></div>
      </div>
      <button class="add" @click="form.bom.push({ part: '', input: '' })">
        <span><svg class="i" viewBox="0 0 24 24" style="width:12px;height:12px"><path d="M12 5v14M5 12h14" /></svg></span>
        부품 추가 — 목록에 없으면 <a href="#" @click.prevent.stop="router.push('/parts')">부품 화면에서 먼저 등록</a>합니다
      </button>
    </div>

    <ActionBar :title="pendingCount ? `미확정 부품 ${pendingCount}개 — 이대로 저장하면 신고할 수 없습니다.` : '부품이 전부 확정입니다 — 저장하면 신고할 수 있습니다.'"
               note="복합키는 product_id + part_id 입니다. 미확정 부품은 벤치마크를 등록하거나 협력사 제출 건을 확정하면 풀립니다">
      <button class="quiet" :disabled="!dirty" @click="form = { euCountry: p.euCountry, tons: String(p.tons), bom: p.parts.map(r => ({ part: r.part, input: String(r.input) })) }">되돌리기</button>
      <button class="btn" :disabled="!dirty || !valid || busy" @click="save">{{ busy ? '저장 중…' : '저장' }}</button>
    </ActionBar>
  </template>
  <div class="spacer"></div>
</template>
