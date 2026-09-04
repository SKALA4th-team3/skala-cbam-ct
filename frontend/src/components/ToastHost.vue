<script setup>
/** 화면 하단 중앙 안내(토스트).
 *
 *  `<Teleport to="body">` 인 이유 — 모달(`.ovl`)과 서랍(`.drawer`)이 같은 방식으로 body 직속에 붙는다.
 *  같은 부모의 형제여야 z-index 비교가 DOM 구조와 무관하게 성립한다.
 *  여기 두지 않으면 저장 실패처럼 **모달을 연 채 뜨는 안내**가 덮개 뒤에 깔린다.
 *
 *  클래스로 쓰는 이유 — 전에는 `id="toast"` 였는데 스타일은 `.toast` 로만 걸려 있어
 *  position·opacity 가 하나도 안 먹고 문서 흐름대로 좌측 최상단에 그려졌다.
 *  훅을 하나로 둬야 같은 어긋남이 다시 안 난다. */
import { useUi } from '@/stores/ui'
const ui = useUi()
</script>

<template>
  <Teleport to="body">
    <div class="toast" :class="{ on: !!ui.toast, run: ui.toast?.sticky }">
      <i v-if="ui.toast?.sticky"></i>{{ ui.toast?.text }}
    </div>
  </Teleport>
</template>
