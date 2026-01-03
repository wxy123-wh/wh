import { createRouter, createWebHistory } from 'vue-router'

import OverviewView from '../views/OverviewView.vue'
import AnalysisView from '../views/AnalysisView.vue'
import ReviewsView from '../views/ReviewsView.vue'
import TopicsView from '../views/TopicsView.vue'
import ClustersView from '../views/ClustersView.vue'
import ClusterDetailView from '../views/ClusterDetailView.vue'
import CompareView from '../views/CompareView.vue'
import AlertsView from '../views/AlertsView.vue'
import SuggestionsView from '../views/SuggestionsView.vue'
import EventsView from '../views/EventsView.vue'
import BeforeAfterView from '../views/BeforeAfterView.vue'
import LoginView from '../views/LoginView.vue'
import DashboardPmView from '../views/DashboardPmView.vue'
import DashboardMarketView from '../views/DashboardMarketView.vue'
import DashboardOpsView from '../views/DashboardOpsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/dashboard/pm', component: DashboardPmView, meta: { roles: ['PM'] } },
    { path: '/dashboard/market', component: DashboardMarketView, meta: { roles: ['MARKET'] } },
    { path: '/dashboard/ops', component: DashboardOpsView, meta: { roles: ['OPS'] } },
    { path: '/overview', component: OverviewView },
    { path: '/analysis', component: AnalysisView },
    { path: '/reviews', component: ReviewsView },
    { path: '/topics', component: TopicsView, meta: { roles: ['MARKET'] } },
    { path: '/clusters', component: ClustersView, meta: { roles: ['PM'] } },
    { path: '/clusters/:id', component: ClusterDetailView, meta: { roles: ['PM'] } },
    { path: '/compare', component: CompareView, meta: { roles: ['MARKET'] } },
    { path: '/alerts', component: AlertsView, meta: { roles: ['OPS'] } },
    { path: '/suggestions', component: SuggestionsView, meta: { roles: ['PM'] } },
    { path: '/events', component: EventsView, meta: { roles: ['PM', 'OPS'] } },
    { path: '/before-after', component: BeforeAfterView, meta: { roles: ['PM', 'OPS'] } },
    { path: '/:pathMatch(.*)*', redirect: '/overview' },
  ],
})

function roleHome(role) {
  const r = String(role || '').toUpperCase()
  if (r === 'PM') return '/dashboard/pm'
  if (r === 'MARKET') return '/dashboard/market'
  if (r === 'OPS') return '/dashboard/ops'
  return '/overview'
}

router.beforeEach((to) => {
  if (to.meta?.public) {
    const token = localStorage.getItem('repu_token')
    const role = localStorage.getItem('repu_role')
    if (to.path === '/login' && token && role) {
      return roleHome(role)
    }
    return true
  }

  const token = localStorage.getItem('repu_token')
  const role = localStorage.getItem('repu_role')
  if (!token || !role) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const allowed = to.meta?.roles
  if (Array.isArray(allowed) && allowed.length > 0 && !allowed.includes(String(role).toUpperCase())) {
    return roleHome(role)
  }

  return true
})

export default router
