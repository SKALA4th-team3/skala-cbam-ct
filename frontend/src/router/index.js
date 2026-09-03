import { createRouter, createWebHistory } from 'vue-router'

/* 화면 ID(S0~S16)는 Figma 04 UI 프레임 이름과 1:1 이다.
   meta.globe 는 배경 구의 카메라 위치 — 화면이 바뀌면 카메라만 움직인다.
   meta.nav 는 상단 메뉴 8개 중 어느 것을 켤지. */
const V = (name, path, comp, nav, globe, title) =>
  ({ name, path, component: comp, meta: { nav, globe, title } })

export const NAV = [
  { key: 'dashboard', label: '관제',   to: '/' },
  { key: 'inbox',     label: '접수함', to: '/inbox' },
  { key: 'suppliers', label: '협력사', to: '/suppliers' },
  { key: 'parts',     label: '부품',   to: '/parts' },
  { key: 'products',  label: '제품',   to: '/products' },
  { key: 'review',    label: '검토',   to: '/review' },
  { key: 'feedback',  label: '피드백', to: '/feedback' },
  { key: 'settings',  label: '설정',   to: '/settings' },
]

const routes = [
  V('landing',   '/landing',            () => import('@/views/LandingView.vue'),        null,        { lat: 12,   lon: 128,   zoom: 1,    cy: .5,  alpha: 1,   deep: false }, '첫 화면'),
  V('dashboard', '/',                   () => import('@/views/DashboardView.vue'),      'dashboard', { lat: 20,   lon: 128,   zoom: 1.25, cy: .46, alpha: .20, deep: false }, '관제'),
  V('inbox',     '/inbox',              () => import('@/views/InboxView.vue'),          'inbox',     { lat: 34.5, lon: 131,   zoom: 2.6,  cy: .64, alpha: .30, deep: false }, '이메일 접수함'),
  V('suppliers', '/suppliers',          () => import('@/views/SuppliersView.vue'),      'suppliers', { lat: 36,   lon: 128,   zoom: 4.5,  cy: .88, alpha: .36, deep: true  }, '협력사'),
  V('supplierNew','/suppliers/new',     () => import('@/views/SupplierNewView.vue'),    'suppliers', { lat: 36.5, lon: 127.8, zoom: 3,    cy: .70, alpha: .28, deep: false }, '협력사 등록'),
  V('supplier',  '/suppliers/:id',      () => import('@/views/SupplierDetailView.vue'), 'suppliers', { lat: 36,   lon: 129.4, zoom: 6.8,  cy: .95, alpha: .24, deep: true  }, '협력사 상세'),
  V('parts',     '/parts',              () => import('@/views/PartsView.vue'),          'parts',     { lat: 40,   lon: 100,   zoom: 1.9,  cy: .52, alpha: .20, deep: false }, '부품 관리'),
  V('products',  '/products',           () => import('@/views/ProductsView.vue'),       'products',  { lat: 51.9, lon: 7,     zoom: 4.5,  cy: .88, alpha: .36, deep: true  }, '완제품'),
  V('productBom','/products/:id/bom',   () => import('@/views/ProductBomView.vue'),     'products',  { lat: 45,   lon: 58,    zoom: 2,    cy: .54, alpha: .18, deep: false }, '제품 · BOM'),
  V('productRep','/products/:id/report',() => import('@/views/ProductReportView.vue'),  'products',  { lat: 51.9, lon: 4.5,   zoom: 6.4,  cy: .94, alpha: .26, deep: true  }, '신고 가능 여부'),
  V('parse',     '/submissions/:id',    () => import('@/views/ParseView.vue'),          'review',    { lat: 36,   lon: 129.4, zoom: 6.4,  cy: .94, alpha: .26, deep: true  }, '자료 변환'),
  V('review',    '/review',             () => import('@/views/ReviewQueueView.vue'),    'review',    { lat: 24,   lon: 122,   zoom: 1.5,  cy: .48, alpha: .22, deep: false }, '검토 대기'),
  V('confirm',   '/review/:id',         () => import('@/views/ReviewConfirmView.vue'),  'review',    { lat: 35.2, lon: 128.6, zoom: 7.4,  cy: .96, alpha: .20, deep: true  }, '검토 · 확정'),
  V('feedback',  '/feedback',           () => import('@/views/FeedbackDraftView.vue'),  'feedback',  { lat: 44,   lon: 66,    zoom: 2.4,  cy: .60, alpha: .22, deep: false }, '안내문 생성'),
  V('dispatch',  '/feedback/dispatch',  () => import('@/views/FeedbackSendView.vue'),   'feedback',  { lat: 30,   lon: 90,    zoom: 1.6,  cy: .50, alpha: .24, deep: false }, '피드백 발송 관리'),
  V('deadlines', '/deadlines',          () => import('@/views/DeadlinesView.vue'),      'dashboard', { lat: 18,   lon: 118,   zoom: 1.4,  cy: .48, alpha: .18, deep: false }, '마감 관리'),
  V('settings',  '/settings',           () => import('@/views/SettingsView.vue'),       'settings',  { lat: -8,   lon: 205,   zoom: 1.7,  cy: .50, alpha: .14, deep: false }, '적격 판정 기준'),
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0, behavior: 'smooth' }),
})

router.afterEach(to => { document.title = 'CBAM CT — ' + (to.meta.title || '') })
