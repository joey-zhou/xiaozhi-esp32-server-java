import type { App } from 'vue'
import { permission } from './permission'

export function setupDirectives(app: App) {
  app.directive('permission', permission)
}
