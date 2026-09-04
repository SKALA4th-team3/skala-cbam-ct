<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Suppliers } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import SubmissionStrip from '@/components/SubmissionStrip.vue'
import StatusChip from '@/components/StatusChip.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ActionBar from '@/components/ActionBar.vue'
import ModalBox from '@/components/ModalBox.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import { useUi } from '@/stores/ui'

const route = useRoute(); const router = useRouter(); const ui = useUi()
const s = ref(null); const missing = ref(null)
async function load() {
  try { s.value = await Suppliers.get(route.params.id) } catch (e) { missing.value = e }
}
onMounted(load)

const sub = computed(() => [s.value?.city, s.value?.item, s.value?.country, s.value?.tie].filter(Boolean).join(' · '))
const active = computed(() => s.value?.tie === '협력유지중')

/* ── 2번 수정 — 담당자명·담당자 이메일·전화번호. 이전 이메일로 접수된 이력은 그대로 유지된다 ── */
const editing = ref(false)
const form = ref({ contact: '', email: '', phone: '' })
const errors = ref([]); const message = ref(''); const busy = ref(false)
function openEdit() { form.value = { contact: s.value.contact ?? '', email: s.value.email ?? '', phone: s.value.phone ?? '' }; errors.value = []; message.value = ''; editing.value = true }
async function saveEdit() {
  busy.value = true; errors.value = []; message.value = ''
  try {
    const before = s.value.email
    s.value = { ...s.value, ...(await Suppliers.update(s.value.id, form.value)) }
    editing.value = false
    ui.say(before !== s.value.email ? '수정했습니다 · 이전 이메일로 접수된 이력은 그대로 남습니다' : '수정했습니다')
  } catch (e) { errors.value = e.details?.fields ?? []; message.value = e.message; ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}

/* ── 6번 협력 끊김 — 마감 대상과 미제출 경보에서 빠진다. 제출 데이터는 삭제하지 않는다 ── */
const cutting = ref(false)
async function cut() {
  try {
    s.value = { ...s.value, ...(await Suppliers.deactivate(s.value.id)) }
    cutting.value = false
    ui.say('협력끊김으로 바꿨습니다 · 마감 대상과 미제출 경보에서 빠지고, 제출 데이터는 보존됩니다')
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
}
const day = v => v ? String(v).replace('T', ' ').slice(0, 16) : '—'
</script>

<template>
  <ViewHead v-if="missing" kicker="없는 협력업체">
    <template #title>없는 협력업체입니다.</template>
    <template #lede>{{ missing.message }} — <code>{{ route.params.id }}</code></template>
    <template #acts><button class="btn" @click="router.push('/suppliers')">협력사 목록으로</button></template>
  </ViewHead>
  <SkeletonRows v-else-if="!s" :rows="6" />

  <template v-else>
    <ViewHead api="UC-01 · 협력업체 · GET /suppliers/{supplierId}" back="협력사" backTo="/suppliers">
      <template #title>{{ s.name }} <StatusChip v-if="!active" label="협력끊김" tone="processing" flat /></template>
      <template #lede>{{ sub }}{{ s.why ? ` · ${s.why}` : '' }}</template>
      <template #acts>
        <button class="quiet" :disabled="!s.latestSubmissionId" @click="router.push(`/submissions/${s.latestSubmissionId}`)">
          {{ s.latestSubmissionId ? '가장 최근 제출본 열기' : '제출본이 없습니다' }}
        </button>
        <button class="quiet" @click="openEdit">담당자 수정</button>
        <button v-if="active" class="quiet danger" @click="cutting = true">협력 끊김</button>
      </template>
    </ViewHead>

    <!-- 5번 — 「담당자 이메일, 연락처를 한 화면에서」. 담당자 이메일은 19번의 매칭 키이기도 하다 -->
    <div class="contact stage" style="--d:100ms">
      <div><span class="cap">담당자</span><b>{{ s.contact ?? '—' }}</b></div>
      <div><span class="cap">담당자 이메일 · 매칭 키</span><b class="mono">{{ s.email ?? '—' }}</b></div>
      <div><span class="cap">전화번호</span><b class="mono">{{ s.phone ?? '—' }}</b></div>
      <div><span class="cap">사업자 등록번호</span><b class="mono">{{ s.bizNo ?? '—' }}</b></div>
    </div>

    <div class="minis stage" style="--d:120ms">
      <div><div class="cap">판정</div><b>{{ s.judgement }}</b><span>이번 달 기준{{ active ? '' : ' · 집계 제외' }}</span></div>
      <div><div class="cap">공급 부품</div><b>{{ s.parts.length }}</b><span>벤치마크 미등록 {{ s.parts.filter(p => !p.factor).length }}</span></div>
      <div><div class="cap">최근 12개월</div><b>{{ s.strip.split('').filter(c => c !== '0').length }}</b><span>문제 있던 달</span></div>
    </div>

    <div class="subhead stage" style="--d:160ms"><h3>최근 12개월 제출 이력</h3><p>붉은 칸은 미제출, 파란 칸은 부적격입니다.</p></div>
    <div class="stage" style="--d:180ms;padding:8px 16px 20px"><SubmissionStrip :pattern="s.strip" axis /></div>

    <!-- 5번 — 생산부품(서브미션) 리스트 · 수신 경보 리스트 · 제출 이력 -->
    <div class="two hist stage" style="--d:200ms">
      <div>
        <div class="subhead"><h3>제출 건 {{ s.submissions.length }}</h3><p>이 협력사가 보낸 제출 데이터입니다.</p></div>
        <div class="list">
          <div v-for="x in s.submissions" :key="x.id" v-clickable class="row al link" :aria-label="`${x.period} 제출 건`" @click="router.push(`/review/${x.id}`)">
            <div class="n"><b>{{ x.period }}</b><span>{{ day(x.submittedAt) }} · {{ x.status }}</span></div>
            <span class="rule">{{ x.rule ?? '—' }}</span><span class="why">{{ x.why }}</span>
            <StatusBadge :value="x.judgement" />
          </div>
          <div v-if="!s.submissions.length" class="noresult"><b>제출 건이 없습니다.</b><p>메일이 들어오면 접수함을 거쳐 여기 나타납니다.</p></div>
        </div>
      </div>
      <div>
        <div class="subhead"><h3>수신 경보 {{ s.alerts.length }}</h3><p>미제출 연속 개월과 걸린 규칙입니다.</p></div>
        <div class="list">
          <div v-for="(a, i) in s.alerts" :key="i" class="row al">
            <div class="n"><b>{{ a.kind }}</b><span>{{ a.at }}</span></div>
            <span class="rule"></span><span class="why">{{ a.text }}</span>
            <StatusBadge :value="a.severity" />
          </div>
          <div v-if="!s.alerts.length" class="noresult"><b>경보가 없습니다.</b><p>이번 달 제출이 정상입니다.</p></div>
        </div>
      </div>
    </div>

    <div class="subhead stage" style="--d:220ms"><h3>공급 부품</h3><p>벤치마크 팩터가 비면 완제품 신고가 막힙니다. 누르면 부품 상세로 갑니다 (10번).</p></div>
    <div class="parts stage" style="--d:240ms">
      <div class="h"><span>부품명</span><span>CN 코드</span><span>단위</span><span>벤치마크 팩터</span></div>
      <div v-for="p in s.parts" :key="p.id" v-clickable class="pt link" :class="{ gap: !p.factor }" :aria-label="`${p.name} 상세`" @click="router.push(`/parts/${p.id}`)">
        <b>{{ p.name }}</b><span class="sup">{{ p.cn }}</span><span class="val">{{ p.unit }}</span>
        <StatusChip :label="p.factor || '벤치마크 미등록'" :tone="p.factor ? 'complete' : 'missing'" />
      </div>
      <div v-if="!s.parts.length" class="noresult"><b>등록된 부품이 없습니다.</b><p>부품 화면에서 이 협력사를 공급처로 지정하면 여기 나타납니다.</p></div>
    </div>

    <!-- 17번·53번 — 발송 이력은 협력업체 상세에서 확인한다 -->
    <div class="subhead stage" style="--d:260ms"><h3>피드백 발송 이력 {{ s.feedbackHistories.length }}</h3>
      <p>발송일 · 제목 · 상태 · 회신 여부. 리마인드 마지막 발송 {{ s.reminders?.lastSent ?? '없음' }}.</p></div>
    <div class="alerts stage" style="--d:280ms">
      <div v-for="h in s.feedbackHistories" :key="h.id" class="at" style="cursor:default">
        <span class="rule">{{ h.rule }}</span>
        <div><b>{{ h.subject }}</b><span class="sub">{{ h.to }}</span></div>
        <span class="why">{{ h.sentAt ? '발송 ' + day(h.sentAt) : '확정 ' + day(h.confirmedAt) }}</span>
        <StatusChip :label="h.state" :tone="h.tone" />
        <span class="ago">{{ h.replied === true ? '회신 있음' : h.replied === false ? '회신 없음' : h.note }}</span>
      </div>
      <div v-if="!s.feedbackHistories.length" class="noresult"><b>보낸 피드백이 없습니다.</b><p>검토에서 반려하면 초안이 만들어지고, 확정·발송하면 여기 남습니다.</p></div>
    </div>

    <ActionBar :title="active ? '제출 이력은 담당자 이메일이 바뀌어도 그대로 남습니다.' : '협력끊김 상태입니다 — 마감 대상과 미제출 경보에서 빠져 있습니다.'"
               :note="active ? '협력끊김으로 바꿔도 기존 제출 데이터는 삭제하지 않습니다' : `끊은 시각 ${day(s.inactiveAt)} · 기존 제출 데이터는 보존됩니다`">
      <button class="btn" :disabled="!s.latestSubmissionId" @click="router.push(`/feedback/${s.latestSubmissionId}`)">안내문 만들기</button>
    </ActionBar>
  </template>

  <!-- 2번 수정 -->
  <ModalBox :open="editing" title="담당자 수정" sub="담당자명 · 담당자 이메일 · 전화번호를 수정합니다. 이메일이 바뀌어도 이전 이메일로 접수된 이력은 그대로 유지됩니다 (2번)" sticky @close="editing = false">
    <div class="form mform">
      <label class="fld" :class="{ bad: errors.includes('contact') }"><span>담당자명</span><input v-model="form.contact" /></label>
      <label class="fld" :class="{ bad: errors.includes('email') }"><span>담당자 이메일 · 매칭 키</span><input v-model="form.email" /></label>
      <label class="fld" :class="{ bad: errors.includes('phone') }"><span>전화번호</span><input v-model="form.phone" /></label>
    </div>
    <p v-if="message" class="formerr" role="alert">{{ message }}</p>
    <template #acts>
      <button class="quiet" @click="editing = false">취소</button>
      <button class="btn" :disabled="busy" @click="saveEdit">{{ busy ? '저장 중…' : '저장' }}</button>
    </template>
  </ModalBox>

  <!-- 6번 협력 끊김 — 되돌리는 API 가 명세에 없어 한 번 더 묻는다 -->
  <ModalBox :open="cutting" title="협력 끊김" :sub="`${s?.name} 을(를) 협력끊김으로 바꿉니다.`" @close="cutting = false">
    <ul class="plain">
      <li>마감 대상과 미제출 경보에서 <b>제외</b>됩니다.</li>
      <li>기존 제출 데이터는 <b>삭제하지 않고 보존</b>합니다.</li>
      <li>되돌리는 기능은 명세에 없습니다 — 다시 협력하려면 팀에 묻습니다.</li>
    </ul>
    <template #acts>
      <button class="quiet" @click="cutting = false">취소</button>
      <button class="btn warn" @click="cut">협력 끊김으로 바꾸기</button>
    </template>
  </ModalBox>
  <div class="spacer"></div>
</template>
