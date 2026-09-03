<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Suppliers } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import SubmissionStrip from '@/components/SubmissionStrip.vue'
import StatusChip from '@/components/StatusChip.vue'
import ActionBar from '@/components/ActionBar.vue'
import { useUi } from '@/stores/ui'

const route = useRoute(); const router = useRouter(); const ui = useUi()
const s = ref(null)
onMounted(async () => { s.value = await Suppliers.get(route.params.id) })
</script>

<template>
  <ViewHead v-if="s" api="UC-01 · 협력업체 · GET /suppliers/{supplierId}" back="협력사" backTo="/suppliers">
    <template #title>{{ s.name }}</template>
    <template #lede>{{ s.city }} · {{ s.item }} · {{ s.country }} · {{ s.tie }} · {{ s.why }}</template>
    <template #acts>
      <button class="quiet" @click="router.push('/submissions/sub-1')">가장 최근 제출본 열기</button>
      <button class="quiet" @click="ui.say('협력끊김으로 바꾸면 마감 대상과 미제출 경보에서 빠집니다')">협력 끊김</button>
    </template>
  </ViewHead>

  <!-- 명세 5번 — 「담당자 이메일, 연락처를 한 화면에서」. 담당자 이메일은 19번의 매칭 키이기도 하다 -->
  <div v-if="s" class="contact stage" style="--d:100ms">
    <div><span class="cap">담당자</span><b>{{ s.contact ?? '—' }}</b></div>
    <div><span class="cap">담당자 이메일 · 매칭 키</span><b class="mono">{{ s.email ?? '—' }}</b></div>
    <div><span class="cap">전화번호</span><b class="mono">{{ s.phone ?? '—' }}</b></div>
    <div><span class="cap">사업자 등록번호</span><b class="mono">{{ s.bizNo ?? '—' }}</b></div>
  </div>

  <div v-if="s" class="minis stage" style="--d:120ms">
    <div><div class="cap">판정</div><b>{{ s.judgement }}</b><span>이번 달 기준</span></div>
    <div><div class="cap">공급 부품</div><b>{{ s.parts.length }}</b><span>벤치마크 미등록 {{ s.parts.filter(p => !p.factor).length }}</span></div>
    <div><div class="cap">최근 12개월</div><b>{{ s.strip.split('').filter(c => c !== '0').length }}</b><span>문제 있던 달</span></div>
  </div>

  <div v-if="s" class="subhead stage" style="--d:160ms"><h3>최근 12개월 제출 이력</h3><p>붉은 칸은 미제출, 파란 칸은 부적격입니다.</p></div>
  <div v-if="s" class="stage" style="--d:180ms;padding:8px 16px 20px"><SubmissionStrip :pattern="s.strip" /></div>

  <div v-if="s" class="subhead stage" style="--d:200ms"><h3>공급 부품</h3><p>벤치마크 팩터가 비면 완제품 신고가 막힙니다.</p></div>
  <div v-if="s" class="parts stage" style="--d:220ms">
    <div class="h"><span>부품명</span><span>CN 코드</span><span>공급 협력업체</span><span>벤치마크 팩터</span></div>
    <div v-for="p in s.parts" :key="p.name" class="pt" :class="{ gap: !p.factor }">
      <b>{{ p.name }}</b><span class="sup">{{ p.cn }}</span><span class="val">{{ p.supplier }}</span>
      <StatusChip :label="p.factor || '벤치마크 미등록'" :tone="p.factor ? 'complete' : 'missing'" />
    </div>
    <div v-if="!s.parts.length" class="noresult"><b>등록된 부품이 없습니다.</b><p>부품 화면에서 이 협력사를 공급처로 지정하면 여기 나타납니다.</p></div>
  </div>

  <ActionBar v-if="s" title="제출 이력은 담당자 이메일이 바뀌어도 그대로 남습니다."
             note="협력끊김으로 바꿔도 기존 제출 데이터는 삭제하지 않습니다">
    <button class="btn" @click="router.push('/feedback')">안내문 만들기</button>
  </ActionBar>
  <div class="spacer"></div>
</template>
