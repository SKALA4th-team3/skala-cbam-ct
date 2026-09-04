<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Parts, Products, allRows } from '@/api'
import ModalBox from '@/components/ModalBox.vue'
import { useUi } from '@/stores/ui'

/** 12번 완제품 등록 — 제품명 · CN코드 · 수출 대상 EU 회원국 · 연간 수출량(T) · 부품 세부.
 *  부품 세부 = ① 부품명 ② 협력사(누락 여부 상관없이 전부 표시) ③ 투입량(t/t) ④ 상태 */
const props = defineProps({ open: Boolean })
const emit = defineEmits(['close', 'created'])
const ui = useUi()
const EU = ['네덜란드', '독일', '벨기에', '프랑스', '이탈리아', '스페인', '폴란드', '체코', '오스트리아', '스웨덴']
const form = ref({ name: '', cn: '', euCountry: '네덜란드', tons: '', bom: [{ part: '', input: '' }] })
const errors = ref([]); const message = ref(''); const busy = ref(false)
const parts = ref([])
onMounted(async () => { parts.value = allRows(await Parts.list({ size: 1000 }), 'GET /parts') })
watch(() => props.open, v => { if (v) { form.value = { name: '', cn: '', euCountry: '네덜란드', tons: '', bom: [{ part: '', input: '' }] }; errors.value = []; message.value = '' } })

const partOf = name => parts.value.find(p => p.name === name)
const cnOk = computed(() => /^\d{8}$/.test(form.value.cn.replace(/\s/g, '')))
const bomOk = computed(() => form.value.bom.length && form.value.bom.every(b => b.part && Number(b.input) > 0))
const canSubmit = computed(() => form.value.name.trim() && cnOk.value && form.value.euCountry && Number(form.value.tons) > 0 && bomOk.value && !busy.value)
const addRow = () => form.value.bom.push({ part: '', input: '' })
const dropRow = i => { if (form.value.bom.length > 1) form.value.bom.splice(i, 1) }

async function submit() {
  errors.value = []; message.value = ''; busy.value = true
  try {
    const row = await Products.create({ ...form.value, name: form.value.name.trim(), tons: Number(form.value.tons) })
    ui.say(`완제품 「${row.name}」 을 등록했습니다 · 부품 ${row.partCount}개`)
    emit('created', row); emit('close')
  } catch (e) { errors.value = e.details?.fields ?? []; message.value = e.message; ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
</script>

<template>
  <ModalBox :open="open" title="완제품 등록" wide sticky
         sub="부품 세부의 협력사는 누락 여부와 상관없이 전부 고를 수 있습니다. 벤치마크가 없는 부품은 「미확정」으로 남고, 그 제품은 신고할 수 없습니다 (12번)"
         @close="emit('close')">
    <div class="form mform">
      <label class="fld" :class="{ bad: errors.includes('name') }">
        <span>제품명 <i v-if="!form.name.trim()" class="req">필수</i></span>
        <input v-model="form.name" placeholder="밸브 C형" />
      </label>
      <label class="fld" :class="{ bad: errors.includes('cn') || (form.cn && !cnOk) }">
        <span>CN 코드 <i v-if="!cnOk" class="req">8자리</i></span>
        <input v-model="form.cn" placeholder="8481 8081" inputmode="numeric" />
      </label>
      <label class="fld" :class="{ bad: errors.includes('euCountry') }">
        <span>수출 대상 EU 회원국</span>
        <select v-model="form.euCountry"><option v-for="c in EU" :key="c" :value="c">{{ c }}</option></select>
      </label>
      <label class="fld" :class="{ bad: errors.includes('tons') }">
        <span>연간 수출량 (t) <i v-if="!(Number(form.tons) > 0)" class="req">필수</i></span>
        <input v-model="form.tons" inputmode="decimal" placeholder="6200" />
      </label>
    </div>

    <div class="subhead" style="padding-top:18px"><h3 style="font-size:15px">부품 세부</h3><p>부품명 · 협력사 · 투입량(t/t) · 상태</p></div>
    <div class="bom bomedit" :class="{ bad: errors.includes('bom') }">
      <div class="h"><span>부품명</span><span>공급 협력사</span><span>투입량 t/t</span><span>상태</span><span></span></div>
      <div v-for="(b, i) in form.bom" :key="i" class="bt">
        <select v-model="b.part" class="cellsel">
          <option value="" disabled>부품을 고르세요</option>
          <option v-for="p in parts" :key="p.id" :value="p.name">{{ p.name }}</option>
        </select>
        <div class="cell">{{ partOf(b.part)?.supplier ?? '—' }}</div>
        <input v-model="b.input" class="cellin" inputmode="decimal" placeholder="1.08" />
        <span class="chip" :class="partOf(b.part) ? (partOf(b.part).factor ? 'c' : 'm') : 'p'">
          {{ partOf(b.part) ? (partOf(b.part).factor ? '확정' : '미확정') : '—' }}
        </span>
        <button type="button" class="cl sm" :disabled="form.bom.length === 1" aria-label="행 삭제" @click="dropRow(i)">✕</button>
      </div>
      <button type="button" class="add" @click="addRow"><span>+</span> 부품 추가</button>
    </div>
    <p v-if="message" class="formerr" role="alert">{{ message }}</p>
    <template #acts>
      <button class="quiet" @click="emit('close')">취소</button>
      <button class="btn" :disabled="!canSubmit" @click="submit">{{ busy ? '등록 중…' : '등록' }}</button>
    </template>
  </ModalBox>
</template>
