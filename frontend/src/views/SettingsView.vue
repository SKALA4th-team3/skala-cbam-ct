<script setup>
import { ref } from 'vue'
import { CHECKS, SEVERITIES } from '@/api'
import ViewHead from '@/components/ViewHead.vue'
import StatusChip from '@/components/StatusChip.vue'
import { useUi } from '@/stores/ui'

const ui = useUi()
/* 판정 규칙은 서버 내부 정의다. API 명세 v10 에 조회 엔드포인트가 없어 화면이 상수로 읽는다. */
const checks = ref(CHECKS); const severities = ref(SEVERITIES)
function pick(c, v) { c.value = v; ui.say(`${c.title} → ${v} · 다음 판정부터 적용됩니다`) }
const badge = s => s === 'HIGH' ? 'missing' : s === 'MEDIUM' ? 'anomaly' : 'expiring'
</script>

<template>
  <ViewHead api="UC-08 · 적격 판정 · 서버 내부 정의 (조회 API 없음)">
    <template #title>적격 판정 기준</template>
    <template #lede>검증 세 가지를 모두 통과하면 적격, 하나라도 걸리면 부적격입니다. 임계값은 다음 판정부터 적용됩니다.</template>
  </ViewHead>

  <div class="stage" style="--d:120ms" id="checks2">
    <div v-for="c in checks" :key="c.key" class="chk"
         :class="{ m: c.tone === 'missing', e: c.tone === 'expiring' }">
      <span class="acc"></span>
      <div>
        <div class="t"><StatusChip :label="c.tag" :tone="c.tone" flat /><b>{{ c.title }}</b></div>
        <p>{{ c.desc }}</p>
      </div>
      <div class="opts">
        <button v-for="o in c.options" :key="o" :class="{ on: c.value === o }" @click="pick(c, o)">{{ o }}</button>
      </div>
    </div>
  </div>

  <div class="vhead stage" style="--d:200ms;margin-top:12px">
    <div class="kicker"><i></i>심각도 부여</div>
    <h2 style="font-size:19px">규칙별 심각도</h2>
    <p>판정 사유로 기록되는 규칙 코드마다 심각도가 정해져 있습니다.</p>
  </div>
  <div class="list stage" style="--d:240ms">
    <div v-for="r in severities" :key="r.rule" class="row al" style="cursor:pointer"
         @click="ui.say(`${r.rule} ${r.name} · 심각도 ${r.severity} · ${r.action}`)">
      <div class="n"><b>{{ r.name }}</b><span>{{ r.desc }}</span></div>
      <span class="rule">{{ r.rule }}</span>
      <span class="why">{{ r.action }}</span>
      <span class="badge" :class="badge(r.severity)">{{ r.severity }}</span>
    </div>
  </div>
  <div class="spacer"></div>
</template>
