import { ref, computed, reactive, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 목록 화면 공통 — 검색 + 패싯 필터 + 정렬.
 *
 * 그룹 안은 OR, 그룹끼리는 AND.
 * 배지 숫자는 전체 데이터에서 세고 화면이 직접 세지 않는다.
 * 목업에서 배지와 실제 행이 어긋났던 게 이걸 안 지켜서였다.
 *
 * 반환값은 reactive 객체다 — ref 를 담은 plain object 를 돌려주면
 * 템플릿에서까지 `t.filtered.value` 를 써야 한다. 화면 코드에 `.value` 가 보이면 안 된다.
 *
 * 검색·필터·정렬을 주소(query)에 싣는다 — `sync: true`.
 * 스토어에만 두면 새로고침에 날아가고, 「미제출만 걸러 둔 목록」을 팀원에게 링크로 못 보낸다.
 * push 가 아니라 replace 다 — 칩을 누를 때마다 방문 기록이 쌓이면 목록을 벗어나려고 뒤로가기를 여섯 번 누른다.
 *
 * @param {import('vue').Ref<Array>} rows  전체 행
 * @param {{ search:string, facets:{key,label,field}[], sorts?:{key,fn}[], sync?:boolean }} cfg
 */
export function useTable(rows, cfg) {
  const q = ref('')
  const picked = ref({})                       // { 국가: ['대한민국'], ... }
  const sortKey = ref(cfg.sorts?.[0]?.key ?? null)
  const open = ref(null)

  if (cfg.sync) {
    const route = useRoute(), router = useRouter()
    let syncing = false
    const read = () => {
      syncing = true
      q.value = String(route.query.q ?? '')
      const next = {}
      for (const f of cfg.facets) {
        const raw = route.query[f.key]
        if (raw) next[f.key] = String(raw).split(',').filter(Boolean)
      }
      picked.value = next
      if (route.query.sort) sortKey.value = String(route.query.sort)
      nextTick(() => { syncing = false })
    }
    const write = () => {
      if (syncing) return
      const out = { ...route.query }
      if (q.value.trim()) out.q = q.value.trim(); else delete out.q
      for (const f of cfg.facets) {
        const on = picked.value[f.key] ?? []
        if (on.length) out[f.key] = on.join(','); else delete out[f.key]
      }
      const dflt = cfg.sorts?.[0]?.key ?? null
      if (sortKey.value && sortKey.value !== dflt) out.sort = sortKey.value; else delete out.sort
      if (JSON.stringify(out) === JSON.stringify(route.query)) return
      router.replace({ query: out })
    }
    read()
    watch([q, picked, sortKey], write, { deep: true })
    /* 뒤로/앞으로 가면 주소가 진실이다 */
    watch(() => route.query, (a, b) => { if (JSON.stringify(a) !== JSON.stringify(b)) read() })
  }

  const counts = computed(() => {
    const out = {}
    for (const f of cfg.facets) {
      out[f.key] = {}
      for (const r of rows.value) {
        const v = r[f.field]
        if (v == null) continue
        out[f.key][v] = (out[f.key][v] || 0) + 1
      }
    }
    return out
  })

  const filtered = computed(() => {
    let list = rows.value
    if (q.value.trim()) {
      const needle = q.value.trim().toLowerCase()
      list = list.filter(r => String(r[cfg.search] ?? '').toLowerCase().includes(needle))
    }
    for (const f of cfg.facets) {
      const on = picked.value[f.key]
      if (on?.length) list = list.filter(r => on.includes(r[f.field]))
    }
    const s = cfg.sorts?.find(x => x.key === sortKey.value)
    return s ? [...list].sort(s.fn) : list
  })

  const chips = computed(() => {
    const out = []
    if (q.value.trim()) out.push({ group: '검색', value: q.value.trim(), clear: () => (q.value = '') })
    for (const f of cfg.facets)
      for (const v of picked.value[f.key] ?? [])
        out.push({ group: f.label, value: v, clear: () => toggle(f.key, v) })
    return out
  })

  function toggle(key, value) {
    const cur = picked.value[key] ?? []
    picked.value = { ...picked.value, [key]: cur.includes(value) ? cur.filter(x => x !== value) : [...cur, value] }
  }
  function clearAll() { q.value = ''; picked.value = {} }
  const isOn = (key, value) => (picked.value[key] ?? []).includes(value)
  const groupCount = key => (picked.value[key] ?? []).length

  return reactive({
    q, picked, sortKey, open, counts, filtered, chips,
    toggle, clearAll, isOn, groupCount,
    total: computed(() => rows.value.length),
  })
}
