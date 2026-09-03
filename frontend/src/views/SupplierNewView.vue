<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Suppliers } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import { useUi } from '@/stores/ui'

const router = useRouter(); const ui = useUi()

/* 요구사항 1번이 입력받는 여섯 항목. 전부 필수다 —
   담당자 이메일이 비면 19번의 매칭 키가 사라지고, 그 협력사에 온 메일은 영원히 「미확인」이 된다. */
const FIELDS = [
  ['name', '협력업체명', '성진스틸'], ['bizNo', '사업자 등록번호', '123-45-67890'],
  ['country', '국가', '대한민국'], ['contact', '담당자명', '김철수'],
  ['email', '담당자 이메일', 'cs.kim@sungjin.example'], ['phone', '전화번호', '054-123-4567'],
]
const form = ref(Object.fromEntries(FIELDS.map(([k]) => [k, k === 'country' ? '대한민국' : ''])))

/** 서버가 400·409 로 짚어 준 필드 — ApiError 는 details 안에 담아 준다 */
const errors = ref([])
const message = ref('')
const submitting = ref(false)

/** 화면에서도 먼저 본다. 서버가 다시 막지만, 누르기 전에 어디가 빈지 보이는 게 낫다 */
const blank = computed(() => FIELDS.filter(([k]) => !form.value[k].trim()).map(([k]) => k))
const canSubmit = computed(() => !blank.value.length && !submitting.value)

async function submit() {
  errors.value = []; message.value = ''
  if (blank.value.length) {
    errors.value = blank.value
    message.value = FIELDS.filter(([k]) => blank.value.includes(k)).map(([, l]) => l).join(' · ') + ' 을(를) 입력해야 합니다'
    return
  }
  submitting.value = true
  try {
    await Suppliers.create(form.value)
    ui.say('등록했습니다 · 담당자 이메일이 수신 메일 매칭 키가 됩니다')
    router.push('/suppliers')
  } catch (e) {
    /* ApiError 의 필드 목록은 details 안에 있다 — e.fields 를 읽고 있어서
       중복·형식 오류가 나도 어느 칸이 문제인지 화면에 표시되지 않았다. */
    errors.value = e.details?.fields ?? []
    message.value = e.message
    ui.say(`${e.status} ${e.code} · ${e.message}`)
  } finally { submitting.value = false }
}
</script>

<template>
  <ViewHead api="UC-01 · 협력업체 · POST /suppliers" back="협력사" backTo="/suppliers">
    <template #title>협력사를 등록합니다.</template>
    <template #lede>여섯 항목 모두 필수입니다. 사업자 등록번호와 담당자 이메일은 중복 등록할 수 없습니다.
      담당자 이메일은 수신 메일을 협력업체와 매칭하는 키입니다.</template>
  </ViewHead>

  <div class="form stage" style="--d:140ms">
    <label v-for="[k, label, ph] in FIELDS" :key="k" class="fld" :class="{ bad: errors.includes(k) }">
      <span>{{ label }}<i v-if="!form[k].trim()" class="req" title="필수">필수</i></span>
      <input v-model="form[k]" :placeholder="ph" :aria-invalid="errors.includes(k)" />
    </label>
  </div>

  <p v-if="message" class="formerr stage" style="--d:160ms" role="alert">{{ message }}</p>

  <ActionBar title="담당자 이메일이 곧 매칭 키입니다."
             note="이메일이 바뀌어도 이전 이메일로 접수된 이력은 그대로 유지됩니다">
    <button class="quiet" @click="router.back()">취소</button>
    <button class="btn" :disabled="!canSubmit" @click="submit">{{ submitting ? '등록 중…' : '등록' }}</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
