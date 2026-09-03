<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Parts, Suppliers, allRows } from '@/api'
import ModalBox from '@/components/ModalBox.vue'
import { useUi } from '@/stores/ui'

/** 7번 부품 등록 — 부품명 · CN코드(8자리) · 공급 협력업체 · 단위(kg/ton/EA).
 *  28번 — `resolves` 를 주면 그 제출 건의 미등록 부품을 이 등록으로 해소한다. */
const props = defineProps({
  open: Boolean,
  preset: { type: Object, default: () => ({}) },       // { name, supplier, resolves:{submissionId,name} }
})
const emit = defineEmits(['close', 'created'])
const ui = useUi()
const UNITS = ['kg', 'ton', 'EA']
const form = ref({ name: '', cn: '', supplier: '', unit: 'ton' })
const errors = ref([]); const message = ref(''); const busy = ref(false)
const suppliers = ref([])
onMounted(async () => { suppliers.value = allRows(await Suppliers.list({ size: 1000 }), 'GET /suppliers') })

function reset() {
  form.value = { name: props.preset.name ?? '', cn: props.preset.cn ?? '', supplier: props.preset.supplier ?? '', unit: props.preset.unit ?? 'ton' }
  errors.value = []; message.value = ''
}
watch(() => props.open, v => { if (v) reset() })

const cnOk = computed(() => /^\d{8}$/.test(form.value.cn.replace(/\s/g, '')))
const canSubmit = computed(() => form.value.name.trim() && cnOk.value && form.value.supplier && form.value.unit && !busy.value)

async function submit() {
  errors.value = []; message.value = ''; busy.value = true
  try {
    const row = await Parts.create({ ...form.value, name: form.value.name.trim(), resolves: props.preset.resolves ?? null })
    ui.say(`부품 「${row.name}」 을 등록했습니다${props.preset.resolves ? ' · 미등록 부품이 해소됐습니다' : ''}`)
    emit('created', row); emit('close')
  } catch (e) { errors.value = e.details?.fields ?? []; message.value = e.message; ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
</script>

<template>
  <ModalBox :open="open" title="부품 등록" sticky
         :sub="preset.resolves ? `미등록 부품 「${preset.resolves.name}」 을 해소합니다 — 정식 부품으로 등록하면 그 제출 건을 확정할 수 있게 됩니다 (28번)` : '부품명이 키입니다. CN 코드는 8자리 숫자, 단위는 kg · ton · EA 중 하나입니다 (7번)'"
         @close="emit('close')">
    <div class="form mform">
      <label class="fld" :class="{ bad: errors.includes('name') }">
        <span>부품명 <i v-if="!form.name.trim()" class="req">필수</i></span>
        <input v-model="form.name" placeholder="아연도금 증기" />
      </label>
      <label class="fld" :class="{ bad: errors.includes('cn') || (form.cn && !cnOk) }">
        <span>CN 코드 <i v-if="!cnOk" class="req">8자리</i></span>
        <input v-model="form.cn" placeholder="2711 2100" inputmode="numeric" />
      </label>
      <label class="fld" :class="{ bad: errors.includes('supplier') }">
        <span>공급 협력업체 <i v-if="!form.supplier" class="req">필수</i></span>
        <select v-model="form.supplier">
          <option value="" disabled>고르세요</option>
          <option value="자사 (포항)">자사 (포항)</option>
          <option v-for="s in suppliers" :key="s.id" :value="s.name">{{ s.name }}{{ s.tie === '협력끊김' ? ' (협력끊김)' : '' }}</option>
        </select>
      </label>
      <label class="fld" :class="{ bad: errors.includes('unit') }">
        <span>단위</span>
        <div class="rj-opts">
          <button v-for="u in UNITS" :key="u" type="button" :class="{ on: form.unit === u }" @click="form.unit = u">{{ u }}</button>
        </div>
      </label>
    </div>
    <p v-if="message" class="formerr" role="alert">{{ message }}</p>
    <template #acts>
      <button class="quiet" @click="emit('close')">취소</button>
      <button class="btn" :disabled="!canSubmit" @click="submit">{{ busy ? '등록 중…' : '등록' }}</button>
    </template>
  </ModalBox>
</template>
