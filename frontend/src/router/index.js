import { createRouter, createWebHistory } from 'vue-router'
import AuditView from '../views/AuditView.vue'
import ChatView from '../views/ChatView.vue'
import { useSessionStore } from '../stores/session'

/* 화면은 2개다 (기획서 5장). 로그인·정책 편집 화면은 이번 범위가 아니다 (0.3) */
const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/chat', name: 'chat', component: ChatView },
  { path: '/admin/audit', name: 'audit', component: AuditView, meta: { adminOnly: true } },
  { path: '/:pathMatch(.*)*', redirect: '/chat' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/*
 * 감사 콘솔은 보안 담당자만 연다.
 *
 * 인증은 없다 — `X-User-Id`는 누구인지 말할 뿐 그 사람임을 증명하지 않는다(0.3).
 * 그럼에도 막는 이유는 이 화면이 남의 프롬프트를 훑는 자리이고, 해제 요청 대기열은
 * 원문까지 여는 자리이기 때문이다. 직원 계정으로 주소만 쳐서 들어가지는 것은
 * "누가 볼 수 있는가"를 화면이 스스로 부정하는 셈이다.
 *
 * 진짜 방어선은 서버에 있다. 목록·확정 API가 SECURITY_ADMIN이 아니면 403을 준다
 * (D24·D25). 여기는 그 경계를 화면에서도 같은 모양으로 보이게 하는 것이다.
 */
router.beforeEach((to) => {
  if (!to.meta?.adminOnly) return true
  const session = useSessionStore()
  // 계정 목록을 아직 못 받았으면 판단할 근거가 없다. 통과시키고 화면이 다시 그린다.
  if (!session.currentUser) return true
  return session.currentUser.role === 'SECURITY_ADMIN' ? true : { name: 'chat' }
})

export default router
