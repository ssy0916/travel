import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Chat from '../views/Chat.vue'
import Profile from '../views/Profile.vue'
import Detail from '../views/Detail.vue'
import Trips from '../views/Trips.vue'
import Favorites from '../views/Favorites.vue'
import History from '../views/History.vue'
import Contacts from '../views/Contacts.vue'
import Notices from '../views/Notices.vue'
import Help from '../views/Help.vue'
import Settings from '../views/Settings.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/chat',
    name: 'Chat',
    component: Chat
  },
  {
    path: '/profile',
    name: 'Profile',
    component: Profile
  },
  {
    // 行程详情/生成结果：?city=&budget=&day= 生成模式，?id= 已保存模式
    path: '/detail',
    name: 'Detail',
    component: Detail
  },
  {
    path: '/trips',
    name: 'Trips',
    component: Trips
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: Favorites
  },
  {
    path: '/history',
    name: 'History',
    component: History
  },
  {
    path: '/contacts',
    name: 'Contacts',
    component: Contacts
  },
  {
    path: '/notices',
    name: 'Notices',
    component: Notices
  },
  {
    path: '/help',
    name: 'Help',
    component: Help
  },
  {
    path: '/settings',
    name: 'Settings',
    component: Settings
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
