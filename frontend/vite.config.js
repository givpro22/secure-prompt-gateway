import { execFileSync } from 'node:child_process'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { fixtureServer } from './dev/fixture-server.js'

/*
 * 정책 버전 = git 커밋. 화면의 버전으로 저장소의 그 시점을 바로 찾을 수 있어야 한다.
 *
 * 커밋 수를 번호로, 짧은 해시를 좌표로 쓴다. `v31 · fad986f`처럼 나온다.
 *
 * 마이그레이션 개수(V1~V5)로 세던 것을 바꾼 것이다. 그쪽이 "정책이 몇 번 바뀌었나"에는
 * 더 정확하지만, 화면에서 본 값으로 커밋을 찾을 수는 없었다. 대신 이제 정책과 무관한
 * 커밋에도 번호가 오른다 — 읽는 사람에게는 정책 개정 횟수가 아니라 빌드 시점이다.
 *
 * git이 없는 환경(배포 이미지 등)에서는 빈 값이 되고 화면은 그 줄을 숨긴다.
 */
function gitVersion() {
  const run = (args) => execFileSync('git', args, { cwd: __dirname, encoding: 'utf8' }).trim()
  try {
    return { count: Number(run(['rev-list', '--count', 'HEAD'])), sha: run(['rev-parse', '--short', 'HEAD']) }
  } catch {
    return { count: 0, sha: '' }
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
      __GIT_VERSION__: JSON.stringify(gitVersion()),
    },
  }
})
