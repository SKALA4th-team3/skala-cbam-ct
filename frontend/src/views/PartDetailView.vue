<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Parts } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import ModalBox from '@/components/ModalBox.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import { useUi } from '@/stores/ui'

const route = useRoute(); const router = useRouter(); const ui = useUi()
const p = ref(null); const missing = ref(null)
async function load() { try { p.value = await Parts.get(route.params.id) } catch (e) { missing.value = e } }
onMounted(load)

/* 8번 — 부품 정보(평균값)를 수정한다. 벤치마크 팩터가 34번 평균값 비교의 기준이다 */
const editing = ref(false)
const form = ref({ factor: '', factorUnit: 't', unit: 'ton' })
const errors = ref([]); const message = ref(''); const busy = ref(false)
function openEdit() {
  form.value = { factor: p.value.factor ? String(parseFloat(p.value.factor)) : '', factorUnit: (p.value.factor?.split('/')?.[1] ?? 't'), unit: p.value.unit ?? 'ton' }
  errors.value = []; message.value = ''; editing.value = true
}
async function save() {
  busy.value = true; errors.value = []; message.value = ''
  try {
    p.value = { ...p.value, ...(await Parts.update(p.value.id, form.value)) }
    editing.value = false; ui.say('수정했습니다 · 다음 평균값 비교부터 이 팩터를 씁니다')
  } catch (e) { errors.value = e.details?.fields ?? []; message.value = e.message; ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
const confirmedRows = computed(() => (p.value?.confirmedData ?? []).flatMap(c => c.rows.map(r => ({ ...r, period: c.period, supplier: c.supplier, confirmedAt: c.confirmedAt, submissionId: c.submissionId }))))
</script>

<template>
  <ViewHead v-if="missing" kicker="없는 부품" back="부품" backTo="/parts">
    <template #title>없는 부품입니다.</template>
    <template #lede>{{ missing.message }} — <code>{{ route.params.id }}</code></template>
  </ViewHead>
  <SkeletonRows v-else-if="!p" :rows="5" />
  <template v-else>
    <ViewHead api="UC-02 · 부품 · GET /parts/{partId}" back="부품" backTo="/parts">
      <template #title>{{ p.name }}</template>
      <template #lede>CN {{ p.cn }} · {{ p.cnGroup }} · 단위 {{ p.unit }} · 공급 {{ p.supplier }}</template>
      <template #acts>
        <button class="quiet" @click="openEdit">벤치마크 · 단위 수정</button>
        <button v-if="p.supplierId" class="quiet" @click="router.push(`/suppliers/${p.supplierId}`)">{{ p.supplier }} 상세</button>
      </template>
    </ViewHead>

    <div class="minis stage" style="--d:120ms">
      <div><div class="cap">벤치마크 팩터</div><b :class="{ nul: !p.factor }">{{ p.factor ?? '미등록' }}</b><span>34번 평균값 비교의 기준</span></div>
      <div><div class="cap">쓰이는 완제품</div><b>{{ p.usedIn.length }}</b><span>{{ p.usedIn.map(u => u.name).join(' · ') || '없음' }}</span></div>
      <div><div class="cap">확정 배출 데이터</div><b>{{ confirmedRows.length }}</b><span>협력업체별 확정 건에서</span></div>
    </div>

    <!-- 10번 — 「협력업체별 확정 배출 데이터를 리스트로」. 확정 건이 없으면 없다고 한다 -->
    <div class="subhead stage" style="--d:160ms"><h3>협력업체별 확정 배출 데이터</h3><p>검토에서 확정(31번)된 제출 건의 표준화 값만 옵니다. 확정 전 값은 여기 없습니다.</p></div>
    <div class="parts cdata stage" style="--d:180ms">
      <div class="h"><span>협력업체 · 기간</span><span>항목</span><span>값</span><span>확정</span></div>
      <div v-for="(r, i) in confirmedRows" :key="i" v-clickable class="pt link" :aria-label="`${r.period} 제출 건`" @click="router.push(`/review/${r.submissionId}`)">
        <b>{{ r.supplier }} · {{ r.period }}</b><span class="sup">{{ r.field }}</span>
        <span class="val">{{ r.value }} {{ r.unit }}</span>
        <StatusChip :label="(r.confirmedAt ?? '').slice(0, 10)" tone="complete" flat />
      </div>
      <div v-if="!confirmedRows.length" class="noresult"><b>확정된 배출 데이터가 없습니다.</b><p>{{ p.supplier }} 의 제출 건이 검토에서 확정되면 여기 쌓입니다.</p></div>
    </div>

    <div v-if="p.usedIn.length" class="subhead stage" style="--d:200ms"><h3>쓰이는 완제품</h3><p>이 부품의 벤치마크가 비면 아래 제품은 신고할 수 없습니다.</p></div>
    <div v-if="p.usedIn.length" class="list stage" style="--d:220ms">
      <div v-for="u in p.usedIn" :key="u.id" v-clickable class="row link" :aria-label="`${u.name} 신고 가능 여부`" @click="router.push(`/products/${u.id}/report`)">
        <div class="n"><b>{{ u.name }}</b><span>신고 가능 여부 보기</span></div><span></span><span></span>
      </div>
    </div>

    <ActionBar :title="p.factor ? '벤치마크가 있어 이 부품이 들어간 완제품을 계산할 수 있습니다.' : '벤치마크가 비어 있습니다 — 이 부품이 들어간 완제품은 신고할 수 없습니다.'"
               note="벤치마크 팩터는 tCO₂e/t 로 적습니다. 모르면 비워 둡니다 — 0 은 「배출이 없다」는 뜻입니다">
      <button class="btn" @click="openEdit">{{ p.factor ? '벤치마크 수정' : '벤치마크 등록' }}</button>
    </ActionBar>
  </template>

  <ModalBox :open="editing" title="부품 수정" sub="벤치마크 팩터(평균값)와 단위를 수정합니다 (8번). 부품명과 CN 코드는 키라 여기서 바꾸지 않습니다." sticky @close="editing = false">
    <div class="form mform">
      <label class="fld" :class="{ bad: errors.includes('factor') }">
        <span>벤치마크 팩터</span>
        <div class="inline"><input v-model="form.factor" inputmode="decimal" placeholder="1.92" /><span class="unitsel">tCO₂e /
          <select v-model="form.factorUnit"><option>t</option><option>MWh</option><option>GJ</option></select></span></div>
      </label>
      <label class="fld" :class="{ bad: errors.includes('unit') }">
        <span>단위</span>
        <div class="rj-opts"><button v-for="u in ['kg', 'ton', 'EA']" :key="u" type="button" :class="{ on: form.unit === u }" @click="form.unit = u">{{ u }}</button></div>
      </label>
    </div>
    <p v-if="message" class="formerr" role="alert">{{ message }}</p>
    <template #acts>
      <button class="quiet" @click="editing = false">취소</button>
      <button class="btn" :disabled="busy" @click="save">{{ busy ? '저장 중…' : '저장' }}</button>
    </template>
  </ModalBox>
  <div class="spacer"></div>
</template>
