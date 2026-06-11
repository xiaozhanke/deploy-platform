import '@/styles/tokens.scss'
import './assets/css/main.css'
import '@/styles/index.scss'
import '@/styles/table.scss'
import 'element-plus/theme-chalk/dark/css-vars.css'

import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { registerGlobalFormat } from './utils'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const authStore = useAuthStore(pinia)
void authStore.loadUser().then(() => {
  app.use(router)
  registerGlobalFormat(app)

  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  app.mount('#app')
})
