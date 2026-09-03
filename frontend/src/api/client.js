import axios from 'axios'
import { contractViolation } from '../lib/contract'

/*
 * baseURL은 환경변수로만 정한다 (11.3 Config Isolation).
 * Postman Mock ↔ 로컬 BE 전환이 `VITE_API_BASE` 한 줄이어야 하므로 URL을 하드코딩하지 않는다.
 */
const baseURL = import.meta.env.VITE_API_BASE

/*
 * 값이 없으면 axios가 상대 경로로 요청해 정적 서버가 index.html을 돌려주고,
 * "JSON을 파싱하지 못했다"는 엉뚱한 에러로 나타난다. 원인이 설정이라는 것을 여기서 밝힌다.
 */
if (!baseURL) {
  contractViolation(
    'api/client',
    'VITE_API_BASE가 비어 있습니다. .env.<mode> 파일이나 빌드 환경변수를 확인하세요',
  )
}

const client = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

/**
 * 요청을 보낼 백엔드를 바꾼다 (시연용 AI 엔진 전환).
 *
 * `@Profile`은 기동 시점에 고정되므로 한 인스턴스 안에서 Mock↔LLM을 바꿀 수 없다.
 * 대신 프로파일이 다른 백엔드를 두 개 띄워 두고 여기서 주소를 바꾼다. 화면·요청·응답
 * 스키마가 전부 같다는 것이 이 전환이 성립하는 이유이며, 그것이 곧 AiInspector가
 * 유일한 교체 지점이라는 증거다 (기획서 9.6).
 */
export function setApiBase(url) {
  client.defaults.baseURL = url
}

/*
 * X-User-Id 주입원. Pinia store를 여기서 import하면
 * client → store → client 순환 참조가 되므로 main.js가 바인딩한다.
 */
let currentUserId = () => null

export function bindUserIdSource(fn) {
  currentUserId = fn
}

client.interceptors.request.use((config) => {
  const userId = currentUserId()
  if (userId !== null && userId !== undefined) {
    config.headers['X-User-Id'] = String(userId)
  }
  return config
})

client.interceptors.response.use(
  (res) => res,
  (err) => {
    /*
     * 403은 통신 실패가 아니라 정상 수행된 정책 판정이다 (계약서 C2).
     * 본문은 에러 봉투가 아니라 판정 객체이므로 `code` 필드를 찾지 않는다.
     * 그대로 reject하면 차단 판정이 화면에서 "통신 오류"로 보인다.
     *
     * `decision === 'BLOCK'`을 확인하는 이유는 판정과 무관한 403을 삼키지 않기 위해서다.
     */
    if (err.response?.status === 403 && err.response.data?.decision === 'BLOCK') {
      return Promise.resolve(err.response)
    }
    return Promise.reject(err)
  },
)

export default client
