import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { fixtureServer } from './dev/fixture-server.js'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  /*
   * 백엔드가 없을 때 5상태를 렌더링해 확인하기 위한 개발 전용 픽스처 서버.
   * `npm run dev:fixtures` (mode=fixtures)에서만 켜지고 빌드 산출물에는 들어가지 않는다.
   */
  const plugins = [vue()]
  if (env.VITE_FIXTURES === '1') plugins.push(fixtureServer())

  return { plugins }
})
