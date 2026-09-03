import { readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { fixtureServer } from './dev/fixture-server.js'

/*
 * 정책 세트 리비전 = 적용된 Flyway 마이그레이션 개수.
 *
 * 정책·규칙이 바뀔 때마다 마이그레이션이 하나씩 늘고 그것이 커밋 하나다. 그래서 이
 * 숫자는 우리 커밋 이력을 그대로 따라간다. 손으로 올리는 값이 아니라 세는 값이라
 * 올리는 걸 잊을 수가 없다.
 *
 * `policy.version`(P-PII v5 같은 것)과 다른 축이다. 저건 정책 하나의 개정 횟수이고
 * 판정 스냅샷 대조에 쓰이며, 이건 정책 세트 전체가 몇 번 바뀌었는가다.
 */
function policyRevision() {
  try {
    const dir = resolve(__dirname, '../backend/src/main/resources/db/migration')
    return readdirSync(dir).filter((f) => /^V\d+__.*\.sql$/.test(f)).length
  } catch {
    return 0
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  /*
   * 백엔드가 없을 때 5상태를 렌더링해 확인하기 위한 개발 전용 픽스처 서버.
   * `npm run dev:fixtures` (mode=fixtures)에서만 켜지고 빌드 산출물에는 들어가지 않는다.
   */
  const plugins = [vue()]
  if (env.VITE_FIXTURES === '1') plugins.push(fixtureServer())

  return {
    plugins,
    define: {
      __POLICY_REVISION__: JSON.stringify(policyRevision()),
    },
  }
})
