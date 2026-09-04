import { createRouter, createWebHistory } from 'vue-router'

/* 화면 ID(S0~S16)는 Figma 04 UI 프레임 이름과 1:1 이다.
   meta.globe 는 배경 구의 카메라 위치 — 화면이 바뀌면 카메라만 움직인다.
   meta.nav 는 상단 메뉴 8개 중 어느 것을 켤지. */
const V = (name, path, comp, nav, globe, title) =>
  ({ name, path, component: comp, meta: { nav, globe, title } })

/* 상단 메뉴 — 성격이 다른 것을 한 줄에 같은 무게로 두지 않는다.
     home   관제 하나
     flow   접수함 → 검토 → 피드백 — «일이 흐르는 순서». 화살표로 잇고 건수를 붙인다
     master 협력사 · 부품 · 제품 — 기준정보. 매일 여는 화면이 아니라 드롭다운 한 단계 밑
     tool   설정 — 오른쪽 아이콘
   meta.nav 는 이 key 를 가리킨다. 마감(관제 밑)·발송 관리(피드백 밑)는 메뉴가 아니라 화면 안의 탭이다. */
export const NAV = [
  { key: 'dashboard', label: '관제',   to: '/',          group: 'home' },
  { key: 'inbox',     label: '접수함', to: '/inbox',     group: 'flow' },
  { key: 'review',    label: '검토',   to: '/review',    group: 'flow' },
  { key: 'feedback',  label: '피드백', to: '/feedback',  group: 'flow' },
  { key: 'suppliers', label: '협력사', to: '/suppliers', group: 'master' },
  { key: 'parts',     label: '부품',   to: '/parts',     group: 'master' },
  { key: 'products',  label: '제품',   to: '/products',  group: 'master' },
  { key: 'settings',  label: '설정',   to: '/settings',  group: 'tool' },
]
export const navOf = key => NAV.find(n => n.key === key) ?? null

const G = (lat, lon, zoom, cy, alpha, deep = false) => ({ lat, lon, zoom, cy, alpha, deep })

const routes = [
  V('landing',    '/landing',              () => import('@/views/LandingView.vue'),        null,        G(12, 128, 1, .5, 1),            '첫 화면'),
  V('dashboard',  '/',                     () => import('@/views/DashboardView.vue'),      'dashboard', G(20, 128, 1.25, .46, .20),      '관제'),
  V('inbox',      '/inbox',                () => import('@/views/InboxView.vue'),          'inbox',     G(34.5, 131, 2.6, .64, .30),     '이메일 접수함'),
  V('suppliers',  '/suppliers',            () => import('@/views/SuppliersView.vue'),      'suppliers', G(36, 128, 4.5, .88, .36, true), '협력사'),
  V('supplierNew','/suppliers/new',        () => import('@/views/SupplierNewView.vue'),    'suppliers', G(36.5, 127.8, 3, .70, .28),     '협력사 등록'),
  V('supplier',   '/suppliers/:id',        () => import('@/views/SupplierDetailView.vue'), 'suppliers', G(36, 129.4, 6.8, .95, .24, true), '협력사 상세'),
  V('parts',      '/parts',                () => import('@/views/PartsView.vue'),          'parts',     G(40, 100, 1.9, .52, .20),       '부품 관리'),
  V('part',       '/parts/:id',            () => import('@/views/PartDetailView.vue'),     'parts',     G(40, 104, 3.2, .70, .24, true), '부품 상세'),
  V('products',   '/products',             () => import('@/views/ProductsView.vue'),       'products',  G(51.9, 7, 4.5, .88, .36, true), '완제품'),
  V('productBom', '/products/:id/bom',     () => import('@/views/ProductBomView.vue'),     'products',  G(45, 58, 2, .54, .18),          '제품 · BOM'),
  V('productRep', '/products/:id/report',  () => import('@/views/ProductReportView.vue'),  'products',  G(51.9, 4.5, 6.4, .94, .26, true), '신고 가능 여부'),
  V('parse',      '/submissions/:id',      () => import('@/views/ParseView.vue'),          'review',    G(36, 129.4, 6.4, .94, .26, true), '자료 변환'),
  V('review',     '/review',               () => import('@/views/ReviewQueueView.vue'),    'review',    G(24, 122, 1.5, .48, .22),       '검토 대기'),
  V('confirm',    '/review/:id',           () => import('@/views/ReviewConfirmView.vue'),  'review',    G(35.2, 128.6, 7.4, .96, .20, true), '검토 · 확정'),
  V('feedback',   '/feedback',             () => import('@/views/FeedbackHubView.vue'),    'feedback',  G(44, 66, 2.4, .60, .22),        '피드백'),
  V('dispatch',   '/feedback/dispatch',    () => import('@/views/FeedbackSendView.vue'),   'feedback',  G(30, 90, 1.6, .50, .24),        '피드백 발송 관리'),
  V('draft',      '/feedback/:id',         () => import('@/views/FeedbackDraftView.vue'),  'feedback',  G(44, 70, 3.2, .66, .22),        '안내문 초안'),
  V('deadlines',  '/deadlines',            () => import('@/views/DeadlinesView.vue'),      'dashboard', G(18, 118, 1.4, .48, .18),       '마감 관리'),
  V('settings',   '/settings',             () => import('@/views/SettingsView.vue'),       'settings',  G(-8, 205, 1.7, .50, .14),       '적격 판정 기준'),
  /* 없는 주소는 관제로 보내지 않는다 — 어디가 잘못됐는지 말한다 */
  { name: 'missing', path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue'), meta: { nav: null, globe: G(12, 128, 1, .5, .3), title: '없는 주소' } },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to, from, saved) => saved ?? { top: 0, behavior: 'smooth' },
})

router.afterEach(to => { document.title = 'CBAM CT — ' + (to.meta.title || '') })
