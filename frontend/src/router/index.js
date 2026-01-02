import { createRouter, createWebHistory } from 'vue-router'

import OverviewView from '../views/OverviewView.vue'
import AnalysisView from '../views/AnalysisView.vue'
import ReviewsView from '../views/ReviewsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/overview', component: OverviewView },
    { path: '/analysis', component: AnalysisView },
    { path: '/reviews', component: ReviewsView },
    { path: '/:pathMatch(.*)*', redirect: '/overview' },
  ],
})

export default router

