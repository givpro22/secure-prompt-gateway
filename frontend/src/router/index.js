import { createRouter, createWebHistory } from 'vue-router'
import AuditView from '../views/AuditView.vue'
import ChatView from '../views/ChatView.vue'

/* 화면은 2개다 (기획서 5장). 로그인·정책 편집 화면은 이번 범위가 아니다 (0.3) */
const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/chat', name: 'chat', component: ChatView },
  { path: '/admin/audit', name: 'audit', component: AuditView },
  { path: '/:pathMatch(.*)*', redirect: '/chat' },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
