import MiragonTheme from '@miragon/docs-starter/theme'
import HomeLanding from './HomeLanding.vue'

export default {
  extends: MiragonTheme,
  enhanceApp({ app }) {
    app.component('HomeLanding', HomeLanding)
  },
}
