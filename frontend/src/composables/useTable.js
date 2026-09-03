import { ref, computed } from 'vue'

/**
 * 목록 화면 공통 — 검색 + 패싯 필터 + 정렬.
 * 그룹 안은 OR, 그룹끼리는 AND.
 * 배지 숫자는 전체 데이터에서 세고 화면이 직접 세지 않는다.
 * 목업에서 배지와 실제 행이 어긋났던 게 이걸 안 지켜서였다.
 *
 * @param {import('vue').Ref<Array>} rows  전체 행
 * @param {{ search:string, facets:{key,label,field}[], sorts?:{key,fn}[] }} cfg
 */
export function useTable(rows, cfg) {
  const q = ref('')
  const picked = ref({})                       // { 국가: ['대한민국'], ... }
  const sortKey = ref(cfg.sorts?.[0]?.key ?? null)
  const open = ref(null)

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

  return { q, picked, sortKey, open, counts, filtered, chips, toggle, clearAll, isOn, groupCount, total: computed(() => rows.value.length) }
}
