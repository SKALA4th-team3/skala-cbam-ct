<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Feedback, Analysis, allRows } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import ActionBar from '@/components/ActionBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import SkeletonRows from '@/components/SkeletonRows.vue'
import ModalBox from '@/components/ModalBox.vue'
import { useUi } from '@/stores/ui'

const route = useRoute(); const router = useRouter(); const ui = useUi()
const sub = ref(null); const draft = ref(null); const versions = ref([]); const missing = ref(null)
const loading = ref(true)
const TONES = ['격식', '간결', '친근']
const tone = ref('격식')

async function load(pick = null) {
  const id = route.params.id
  try { sub.value = await Analysis.get(id) } catch (e) { missing.value = e; loading.value = false; return }
  versions.value = allRows(await Feedback.versions(id), 'GET /feedback-drafts')
  if (!versions.value.length) { draft.value = await Feedback.draft(id, tone.value); versions.value = allRows(await Feedback.versions(id), 'GET /feedback-drafts') }
  else draft.value = pick ? versions.value.find(v => v.id === pick) : versions.value.at(-1)
  tone.value = draft.value.style
  text.value = draft.value.body.join('\n\n')
  loading.value = false
}
onMounted(load)
watch(() => route.params.id, () => { loading.value = true; load() })

const locked = computed(() => !!draft.value?.locked)
const latest = computed(() => versions.value.at(-1))
const isLatest = computed(() => draft.value?.id === latest.value?.id)
const TONE = { 초안: 'processing', 수정본: 'expiring', '발송 대기': 'complete', 폐기: 'missing' }

/* 44번 문체 · 45번 추가 지시 — 재생성. 이전 초안은 버전으로 남는다 */
const instruction = ref('')
const busy = ref(false)
async function regenerate(style = tone.value) {
  if (locked.value) return ui.say('확정된 초안은 다시 만들 수 없습니다 (48번)')
  busy.value = true
  try {
    const d = await Feedback.regenerate(latest.value.id, { style, instruction: instruction.value })
    ui.say(d.failed ? 'AI 가 실패해 기본 템플릿으로 대신했습니다 (46번)' : `v${d.version} · ${style}${instruction.value ? ' · 지시 반영' : ''}`)
    instruction.value = ''
    await load(d.id)
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
function pickTone(t) { if (t !== tone.value) { tone.value = t; regenerate(t) } }

/* 47번 — 담당자가 문안을 직접 고친다. 수정본은 AI 초안과 별도 버전으로 저장 */
const text = ref('')
const dirty = computed(() => draft.value && text.value !== draft.value.body.join('\n\n'))
async function saveEdit() {
  busy.value = true
  try { const d = await Feedback.edit(latest.value.id, text.value); ui.say(`수정본 v${d.version} 으로 저장했습니다`); await load(d.id) }
  catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}

/* 48번 확정 — 수신자·제목·본문이 잠기고 발송 대기가 된다 */
async function confirm() {
  if (dirty.value) return ui.say('고친 내용을 먼저 저장하거나 되돌리세요')
  busy.value = true
  try { const r = await Feedback.confirm(latest.value.id); ui.say(`확정했습니다 · ${r.recipient} 에게 보낼 준비가 됐습니다`); router.push('/feedback/dispatch') }
  catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}

/* 49번 폐기 — 사유를 기록하고 재생성으로 돌아간다 */
const discarding = ref(false); const discardReason = ref('')
async function discard() {
  busy.value = true
  try {
    await Feedback.discard(latest.value.id, discardReason.value)
    discarding.value = false; discardReason.value = ''
    ui.say('폐기했습니다 · 사유를 기록했습니다 — 다시 만듭니다')
    await regenerate(tone.value)
  } catch (e) { ui.say(`${e.status} ${e.code} · ${e.message}`) }
  finally { busy.value = false }
}
async function copy() {
  try { await navigator.clipboard.writeText(text.value); ui.say('안내문을 클립보드에 복사했습니다') }
  catch { ui.say('클립보드에 쓸 수 없습니다 — 본문을 직접 선택해 복사하세요') }
}
const b = computed(() => draft.value?.basis)
</script>

<template>
  <ViewHead v-if="missing" kicker="없는 제출 건" back="피드백" backTo="/feedback">
    <template #title>없는 제출 건입니다.</template>
    <template #lede>{{ missing.message }} — <code>{{ route.params.id }}</code></template>
  </ViewHead>
  <SkeletonRows v-else-if="loading" :rows="4" />
  <template v-else>
    <ViewHead api="UC-10 · 피드백 초안 · POST /feedback-drafts" back="피드백" backTo="/feedback">
      <template #title>{{ draft.supplier }}에 보낼 안내문 <StatusChip :label="draft.status" :tone="TONE[draft.status]" flat /></template>
      <template #lede>v{{ draft.version }} / {{ versions.length }} · {{ draft.source }} · {{ draft.style }} · 수신 {{ draft.to ?? '담당자 이메일 없음' }} · 제목 「{{ draft.subject }}」</template>
      <template #acts>
        <button class="quiet sm" @click="router.push(`/review/${sub.id}`)">검토 화면</button>
        <button class="quiet sm" @click="router.push(`/suppliers/${sub.supplierId}`)">{{ sub.supplier }} 상세</button>
      </template>
    </ViewHead>

    <!-- 46번 — AI 실패를 숨기지 않는다. 기본 템플릿임을 말한다 -->
    <div v-if="draft.failed" class="alert stage" style="--d:80ms">
      <span class="ic">!</span>
      <div><b>AI 초안 실패 · {{ draft.error?.code }}</b><p>{{ draft.error?.message }} — 아래 본문은 기본 템플릿입니다. 그대로 보내지 말고 담당자가 고쳐서 확정하세요 (46·47번).</p></div>
    </div>

    <div class="two stage" style="--d:120ms">
      <!-- 44번 — 판정 근거와 나란히 -->
      <div class="card basis">
        <div class="cap">판정 근거 (37번)</div>
        <h4>{{ b.rule ?? '규칙 없음' }} · {{ b.why }}</h4>
        <div class="sub">판정 {{ b.judgement }}{{ b.severity ? ` · 심각도 ${b.severity}` : '' }} · {{ b.period }}</div>
        <hr />
        <div class="cap">missingFields</div>
        <template v-if="b.missingFields.length"><div v-for="f in b.missingFields" :key="f" class="mf"><i></i>{{ f }}</div></template>
        <div v-else class="sub">없음</div>
        <template v-if="b.unmappedParts.length"><div class="cap" style="margin-top:12px">미등록 부품</div><div v-for="p in b.unmappedParts" :key="p" class="mf"><i></i>{{ p }}</div></template>
        <hr />
        <div class="cap">반려 사유 (32번)</div>
        <p class="sub">{{ b.rejectReason ?? '아직 반려하지 않았습니다' }}</p>
        <hr />
        <div class="dl"><span class="sub">회신 기한</span><b>{{ b.deadline }}</b></div>

        <hr />
        <div class="cap">버전 (45번)</div>
        <div class="versions">
          <button v-for="v in versions" :key="v.id" class="vtab" :class="{ on: v.id === draft.id, dead: v.status === '폐기' }" @click="load(v.id)">
            v{{ v.version }} <em>{{ v.source }} · {{ v.style }}{{ v.status === '폐기' ? ' · 폐기' : v.status === '수정본' ? ' · 수정' : '' }}</em>
          </button>
        </div>
      </div>

      <div>
        <div class="tone">
          <span class="cap">문체</span>
          <button v-for="t in TONES" :key="t" :class="{ on: tone === t }" :disabled="locked || busy" @click="pickTone(t)">{{ t }}</button>
          <span v-if="!isLatest" class="sub" style="margin-left:auto">이전 버전을 보는 중 — 고치려면 최신 버전으로</span>
        </div>
        <!-- 47번 — 본문은 바로 고친다. 잠긴 초안은 읽기 전용 -->
        <div class="letter edit" :class="{ locked }">
          <textarea v-model="text" :readonly="locked || !isLatest" :class="{ shim: busy }" rows="14" spellcheck="false" aria-label="안내문 본문" />
          <div class="letter-foot">
            <span v-if="locked">확정됨 · 수신자 · 제목 · 본문이 잠겨 있습니다 (48번)</span>
            <span v-else-if="dirty">고친 내용이 있습니다 — 저장하면 수정본 버전이 됩니다 (47번)</span>
            <span v-else>{{ draft.instruction ? `추가 지시 「${draft.instruction}」 반영` : '문단은 빈 줄로 나눕니다' }}</span>
            <button v-if="dirty" class="quiet sm" @click="text = draft.body.join('\n\n')">되돌리기</button>
            <button v-if="dirty" class="btn sm" :disabled="busy" @click="saveEdit">수정본 저장</button>
          </div>
        </div>
        <!-- 45번 — 추가 지시(항목 강조, 기한 명시)를 입력해 재생성 -->
        <div v-if="!locked" class="regen">
          <input v-model="instruction" placeholder="추가 지시 — 예) 기한을 강조, 증기 단위를 먼저" :disabled="busy" @keyup.enter="regenerate()" />
          <button class="quiet sm" :disabled="busy" @click="regenerate()">재생성</button>
        </div>
      </div>
    </div>

    <ActionBar :title="locked ? '확정된 초안입니다 — 발송 관리에서 보냅니다.' : '확정하면 수신자 · 제목 · 본문이 잠깁니다.'"
               note="왼쪽은 근거(판정 사유 · missingFields · 반려 사유 · 기한), 오른쪽은 사람이 고칠 수 있는 문안. 근거 없는 문장을 만들지 않습니다">
      <button class="quiet" @click="copy">복사</button>
      <button v-if="!locked" class="quiet danger" :disabled="busy" @click="discarding = true">폐기</button>
      <button v-if="!locked" class="btn" :disabled="busy || !draft.to" @click="confirm">확정하고 발송 관리로</button>
      <button v-else class="btn" @click="router.push('/feedback/dispatch')">발송 관리로</button>
    </ActionBar>
  </template>

  <ModalBox :open="discarding" title="초안 폐기" sub="폐기 사유를 기록하고 재생성으로 돌아갑니다 (49번). 폐기한 버전은 버전 목록에 남습니다." @close="discarding = false">
    <label class="fld"><span>폐기 사유 <i v-if="!discardReason.trim()" class="req">필수</i></span>
      <textarea v-model="discardReason" rows="3" placeholder="예) 근거가 된 판정이 바뀌어 문안이 맞지 않음" /></label>
    <template #acts>
      <button class="quiet" @click="discarding = false">취소</button>
      <button class="btn warn" :disabled="!discardReason.trim() || busy" @click="discard">폐기하고 다시 만들기</button>
    </template>
  </ModalBox>
  <div class="spacer"></div>
</template>
