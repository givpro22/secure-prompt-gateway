import { defineStore } from 'pinia'
import { fetchDepartments, fetchPolicies, fetchUsers } from '../api/catalog'
import { setApiBase } from '../api/client'
import { errorText } from '../lib/contract'

const STORAGE_KEY = 'gateway.currentUserId'

/*
 * AI 엔진 전환 (시연 전용).
 *
 * VITE_API_BASE_LLM이 있을 때만 셀렉터가 뜬다. 배포본은 이 변수를 주지 않으므로
 * EC2 화면에는 나타나지 않는다 — 서버가 7B를 못 돌리는데 고를 수 있게 두면
 * 누르는 순간 실패한다.
 *
 * 엔진 선택은 저장하지 않는다. 새로고침하면 항상 Mock으로 돌아간다. 시연 중
 * 브라우저를 새로 열었을 때 LLM 쪽에 붙어 있으면 그 사실을 눈치채기 어렵다.
 */
const ENGINES = [
  { id: 'mock', label: 'Mock', base: import.meta.env.VITE_API_BASE },
  { id: 'llm', label: '로컬 LLM', base: import.meta.env.VITE_API_BASE_LLM },
].filter((e) => Boolean(e.base))

/*
 * 새로고침해도 계정이 유지되도록 선택만 보관한다. 그 외 상태는 저장하지 않는다.
 * localStorage는 시크릿 창·저장소 차단 설정에서 접근 자체가 던지므로 감싼다.
 */
function readStoredUserId() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const parsed = Number(raw)
    return raw !== null && Number.isFinite(parsed) ? parsed : null
  } catch {
    return null
  }
}

function writeStoredUserId(userId) {
  try {
    window.localStorage.setItem(STORAGE_KEY, String(userId))
  } catch {
    /* 저장 못 해도 화면 동작에는 영향이 없다 */
  }
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    users: [],
    departments: [],
    /** 데모 기본 계정: 이OO(개발팀) — Case A·C 계정 (기획서 10.4) */
    currentUserId: readStoredUserId() ?? 1,
    /** GET /policies?deptId= 응답. 캡션의 정책 건수는 이 배열 길이다 (D8, 하드코딩 금지) */
    policies: [],
    policiesLoaded: false,
    loadError: '',
    /** 'mock' | 'llm'. 엔진이 하나뿐이면 셀렉터가 뜨지 않는다 */
    engineId: ENGINES[0]?.id ?? 'mock',
    engines: ENGINES,
  }),

  getters: {
    currentUser: (state) => state.users.find((u) => u.userId === state.currentUserId) ?? null,
    currentDeptId() {
      return this.currentUser ? this.currentUser.department.deptId : null
    },
    currentDeptName() {
      return this.currentUser ? this.currentUser.department.name : ''
    },
    /** 감사 콘솔 부서 필터에서 정보보안팀을 제외한다 (D2 — 검토자 역할이라 항상 0건) */
    filterDepartments: (state) => state.departments.filter((d) => d.code !== 'INFOSEC'),
    /** 선택지가 둘 이상일 때만 헤더에 셀렉터를 노출한다 */
    engineSwitchable: (state) => state.engines.length > 1,
    currentEngine: (state) => state.engines.find((e) => e.id === state.engineId) ?? null,
  },

  actions: {
    async loadDirectory() {
      this.loadError = ''
      try {
        const [users, departments] = await Promise.all([fetchUsers(), fetchDepartments()])
        this.users = users.items
        this.departments = departments.items
        if (!this.users.some((u) => u.userId === this.currentUserId) && this.users.length > 0) {
          this.currentUserId = this.users[0].userId
        }
        await this.loadPolicies()
      } catch (err) {
        this.loadError = errorText(err, '사용자·부서 목록을 불러오지 못했습니다.')
      }
    },

    async loadPolicies() {
      this.policiesLoaded = false
      const deptId = this.currentDeptId
      if (deptId === null) return
      try {
        const res = await fetchPolicies(deptId)
        this.policies = res.items
        this.policiesLoaded = true
      } catch (err) {
        this.policies = []
        this.loadError = errorText(err, '적용 정책을 불러오지 못했습니다.')
      }
    },

    /**
     * 백엔드를 바꾼다. 인스턴스가 다르면 계정·부서·정책도 다시 읽어야 한다 —
     * 같은 시드를 쓰지만 서로 다른 DB일 수 있다.
     */
    async setEngine(engineId) {
      const engine = this.engines.find((e) => e.id === engineId)
      if (!engine || engineId === this.engineId) return
      this.engineId = engineId
      setApiBase(engine.base)
      await this.loadDirectory()
    },

    async setCurrentUser(userId) {
      if (userId === this.currentUserId) return
      this.currentUserId = userId
      writeStoredUserId(userId)
      await this.loadPolicies()
    },
  },
})
