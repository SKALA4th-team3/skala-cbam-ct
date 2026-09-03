<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
/** 검색 + 패싯 필터 + 정렬 + 결과 수. 협력사·부품·완제품이 같은 것을 쓴다. */
const props = defineProps({
  table: Object,        // useTable() 반환값
  facets: Array,        // [{ key, label, field }]
  sorts: { type: Array, default: () => [] },
  placeholder: String,
  unit: { type: String, default: '건' },
})
const t = props.table
const close = () => (t.open.value = null)
onMounted(() => document.addEventListener('click', close))
onBeforeUnmount(() => document.removeEventListener('click', close))
function toggleMenu(key, e) { e.stopPropagation(); t.open.value = t.open.value === key ? null : key }
</script>

<template>
  <div class="toolbar stage" style="--d:100ms">
    <div class="search">
      <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></svg>
      <input type="text" v-model="t.q.value" :placeholder="placeholder" />
    </div>

    <div v-for="f in facets" :key="f.key" class="fdrop"
         :class="{ open: t.open.value === f.key, has: t.groupCount(f.key) > 0 }">
      <button @click="toggleMenu(f.key, $event)">
        {{ f.label }}<b v-if="t.groupCount(f.key)">{{ t.groupCount(f.key) }}</b><span class="cv"></span>
      </button>
      <div class="menu" @click.stop>
        <label v-for="(n, v) in t.counts.value[f.key]" :key="v"
               :class="{ on: t.isOn(f.key, v) }" @click="t.toggle(f.key, v)">
          <span class="box"></span>{{ v }}<em>{{ n }}</em>
        </label>
      </div>
    </div>

    <div v-if="sorts.length" class="fdrop has" :class="{ open: t.open.value === '__sort' }">
      <button @click="toggleMenu('__sort', $event)">정렬<span class="cv"></span></button>
      <div class="menu" @click.stop>
        <label v-for="s in sorts" :key="s.key" :class="{ on: t.sortKey.value === s.key }"
               @click="t.sortKey.value = s.key; t.open.value = null">
          <span class="box"></span>{{ s.label }}<em>{{ s.key === sorts[0].key ? '기본' : '' }}</em>
        </label>
      </div>
    </div>

    <span class="tcount">
      <b>{{ t.filtered.value.length }}</b> / {{ t.total.value }} {{ unit }}
      <template v-if="t.filtered.value.length < t.total.value"> · 걸러짐</template>
    </span>
  </div>

  <div class="applied stage" style="--d:140ms">
    <span v-for="(c, i) in t.chips.value" :key="i" class="fc">
      <span>{{ c.group }} · {{ c.value }}</span><i @click="c.clear()">×</i>
    </span>
    <button v-if="t.chips.value.length" class="clear" @click="t.clearAll()">전체 해제</button>
  </div>
</template>
