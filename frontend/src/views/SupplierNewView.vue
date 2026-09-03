<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Suppliers } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import { useUi } from '@/stores/ui'

const router = useRouter(); const ui = useUi()
const form = ref({ name: '', bizNo: '', country: '대한민국', contact: '', email: '', phone: '' })
const errors = ref([])
const FIELDS = [
  ['name', '협력업체명', '성진스틸'], ['bizNo', '사업자 등록번호', '123-45-67890'],
  ['country', '국가', '대한민국'], ['contact', '담당자명', '김철수'],
  ['email', '담당자 이메일', 'cs.kim@sungjin.co.kr'], ['phone', '전화번호', '054-123-4567'],
]
async function submit() {
  errors.value = []
  try {
    await Suppliers.create(form.value)
    ui.say('등록했습니다 · 담당자 이메일이 수신 메일 매칭 키가 됩니다')
    router.push('/suppliers')
  } catch (e) { errors.value = e.fields ?? []; ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
</script>

<template>
  <ViewHead api="UC-01 · 협력업체 · POST /suppliers" back="협력사" backTo="/suppliers">
    <template #title>협력사를 등록합니다.</template>
    <template #lede>사업자 등록번호와 담당자 이메일은 중복 등록할 수 없습니다. 담당자 이메일은 수신 메일을 협력업체와 매칭하는 키입니다.</template>
  </ViewHead>

  <div class="form stage" style="--d:140ms">
    <label v-for="[k, label, ph] in FIELDS" :key="k" class="fld" :class="{ bad: errors.includes(k) }">
      <span>{{ label }}</span>
      <input v-model="form[k]" :placeholder="ph" />
    </label>
  </div>

  <ActionBar title="담당자 이메일이 곧 매칭 키입니다."
             note="이메일이 바뀌어도 이전 이메일로 접수된 이력은 그대로 유지됩니다">
    <button class="quiet" @click="router.back()">취소</button>
    <button class="btn" @click="submit">등록</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
