import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { bindUserIdSource } from './api/client'
import { useSessionStore } from './stores/session'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

/*
 * Axios 요청 인터셉터가 X-User-Id에 넣을 값을 여기서 연결한다.
 * client.js가 store를 직접 import하면 순환 참조가 된다.
 */
bindUserIdSource(() => useSessionStore().currentUserId)

app.mount('#app')
