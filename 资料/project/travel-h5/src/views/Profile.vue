<template>
  <div class="profile-page">
    <!-- 顶部用户信息区 -->
    <div class="profile-header">
      <div class="user-box" @click="isLogin ? openEdit() : openLogin()">
        <div class="avatar">
          <van-icon v-if="!isLogin" name="user-o" size="34" color="#C4473A" />
          <span v-else class="avatar-text">{{ avatarText }}</span>
        </div>
        <div class="user-meta">
          <div class="nickname">{{ isLogin ? user.nickname : '点击登录' }}</div>
          <div class="user-desc">
            {{ isLogin ? (maskedPhone || '欢迎回来，开启新旅程') : '登录后同步你的行程数据' }}
          </div>
        </div>
        <van-icon
          :name="isLogin ? 'edit' : 'arrow'"
          size="18"
          color="#C4473A"
          class="user-action"
        />
      </div>
    </div>

    <div class="profile-content">
      <!-- 数据统计 -->
      <div class="card stats-card">
        <div v-for="item in stats" :key="item.label" class="stat-item">
          <div class="stat-num">{{ item.num }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>

      <!-- 我的服务 -->
      <div class="card">
        <div class="section-title">我的服务</div>
        <div class="service-grid">
          <div
            v-for="svc in services"
            :key="svc.label"
            class="service-item"
            @click="onServiceClick(svc)"
          >
            <div class="service-icon" :style="{ backgroundColor: svc.bg }">
              <van-icon :name="svc.icon" size="24" :color="svc.color" />
            </div>
            <span>{{ svc.label }}</span>
          </div>
        </div>
      </div>

      <!-- 功能菜单 -->
      <div class="card menu-card">
        <van-cell
          v-for="menu in menus"
          :key="menu.label"
          is-link
          @click="menu.action"
        >
          <template #icon>
            <van-icon :name="menu.icon" size="18" color="#C4473A" class="menu-icon" />
          </template>
          <template #title>
            <span class="menu-title">
              {{ menu.label }}
              <span v-if="menu.badge" class="red-dot"></span>
            </span>
          </template>
        </van-cell>
      </div>

      <van-button
        v-if="isLogin"
        block
        round
        class="logout-btn"
        @click="onLogout"
      >
        退出登录
      </van-button>
    </div>

    <!-- 登录 / 编辑资料弹窗 -->
    <van-popup v-model:show="showPopup" round position="bottom">
      <div class="popup-title">{{ popupMode === 'edit' ? '编辑资料' : '登录' }}</div>
      <van-field
        v-model="form.nickname"
        label="昵称"
        label-width="60px"
        placeholder="请输入昵称"
        clearable
      />
      <van-field
        v-model="form.phone"
        label="手机号"
        label-width="60px"
        type="tel"
        maxlength="11"
        placeholder="请输入手机号（选填）"
        clearable
      />
      <div class="popup-actions">
        <van-button type="primary" block round @click="saveUser">
          {{ popupMode === 'edit' ? '保存' : '登录' }}
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import {
  userStore,
  tripStore,
  favoriteStore,
  historyStore,
  noticeStore
} from '../utils/storage.js'

const router = useRouter()

const user = ref(userStore.get())
const isLogin = computed(() => !!user.value)
const avatarText = computed(() => user.value?.nickname?.charAt(0) || '游')
const maskedPhone = computed(() => {
  const phone = user.value?.phone
  if (!phone || phone.length !== 11) return ''
  return phone.slice(0, 3) + '****' + phone.slice(7)
})

// 统计数字：行程数来自后端接口（异步），收藏/足迹仍为本地存储
const tripTotal = ref(0)
const stats = computed(() => {
  if (!isLogin.value) {
    return [
      { num: '--', label: '我的行程' },
      { num: '--', label: '我的收藏' },
      { num: '--', label: '足迹城市' }
    ]
  }
  const cityCount = new Set(historyStore.list().map((h) => h.city)).size
  return [
    { num: tripTotal.value, label: '我的行程' },
    { num: favoriteStore.list().length, label: '我的收藏' },
    { num: cityCount, label: '足迹城市' }
  ]
})

const services = [
  { label: '我的行程', icon: 'location-o', color: '#C4473A', bg: 'rgba(196,71,58,0.1)', path: '/trips' },
  { label: '我的收藏', icon: 'star-o', color: '#D9A441', bg: 'rgba(217,164,65,0.12)', path: '/favorites' },
  { label: '浏览历史', icon: 'clock-o', color: '#2F7F7A', bg: 'rgba(47,127,122,0.1)', path: '/history' },
  { label: '出行人', icon: 'friends-o', color: '#1F2328', bg: 'rgba(31,35,40,0.08)', path: '/contacts' }
]

const unreadCount = ref(0)
onMounted(async () => {
  unreadCount.value = noticeStore.unreadCount()
  try {
    const res = await tripStore.list({ page: 0, size: 1 })
    tripTotal.value = res.total || 0
  } catch {
    // 后端不可达时保持 0，不阻塞页面
  }
})

const showPopup = ref(false)
const popupMode = ref('login')
const form = reactive({ nickname: '', phone: '' })

const openLogin = () => {
  popupMode.value = 'login'
  form.nickname = ''
  form.phone = ''
  showPopup.value = true
}

const openEdit = () => {
  popupMode.value = 'edit'
  form.nickname = user.value?.nickname || ''
  form.phone = user.value?.phone || ''
  showPopup.value = true
}

const saveUser = () => {
  const nickname = form.nickname.trim()
  if (!nickname) {
    return showToast('请输入昵称')
  }
  if (form.phone && !/^1\d{10}$/.test(form.phone)) {
    return showToast('手机号格式不正确')
  }
  // 登录时保留已有的其他字段，编辑时整体更新资料字段
  const prev = user.value || {}
  user.value = {
    ...prev,
    nickname,
    phone: form.phone
  }
  userStore.set(user.value)
  showPopup.value = false
  showToast(popupMode.value === 'login' ? '登录成功' : '保存成功')
}

const onLogout = () => {
  showConfirmDialog({
    title: '提示',
    message: '确定要退出登录吗？'
  })
    .then(() => {
      user.value = null
      userStore.clear()
      showToast('已退出登录')
    })
    .catch(() => {})
}

const onServiceClick = (svc) => {
  if (!isLogin.value) {
    showToast('请先登录')
    openLogin()
    return
  }
  router.push(svc.path)
}

const goNotices = () => {
  router.push('/notices')
}

const menus = computed(() => [
  {
    label: '消息通知',
    icon: 'bell',
    badge: unreadCount.value > 0,
    action: goNotices
  },
  { label: '帮助与客服', icon: 'service-o', action: () => router.push('/help') },
  { label: '设置', icon: 'setting-o', action: () => router.push('/settings') }
])
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background-color: var(--paper-bg);
}

/* 顶部用户区：米白纸底 + 邮戳装饰 */
.profile-header {
  background: var(--paper-bg);
  border-bottom: 1px solid var(--paper-line);
  padding: calc(env(safe-area-inset-top, 0px) + 40px) 20px 58px;
  position: relative;
  overflow: hidden;
}
/* 大邮戳圆环装饰：右上角，完整显示 */
.profile-header::before {
  content: '';
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 24px);
  right: 20px;
  width: 88px;
  height: 88px;
  border: 3px solid var(--stamp-red);
  border-radius: 50%;
  opacity: 0.1;
  transform: rotate(-12deg);
  pointer-events: none;
}
/* 内圈虚线，模拟邮戳齿孔 */
.profile-header::after {
  content: '';
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 34px);
  right: 30px;
  width: 68px;
  height: 68px;
  border: 2px dashed var(--stamp-red);
  border-radius: 50%;
  opacity: 0.14;
  transform: rotate(-12deg);
  pointer-events: none;
}
.user-box {
  display: flex;
  align-items: center;
  gap: 14px;
  position: relative;
  z-index: 1;
}
.avatar {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background-color: rgba(196, 71, 58, 0.07);
  border: 2px solid var(--stamp-red);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-text {
  font-size: 26px;
  font-weight: 600;
  color: var(--stamp-red);
  font-family: var(--font-serif);
}
.user-meta {
  flex: 1;
  min-width: 0;
}
.nickname {
  font-size: 20px;
  font-weight: 600;
  color: var(--ink);
  font-family: var(--font-serif);
}
.user-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--van-text-color-3);
}
.user-action {
  flex-shrink: 0;
  opacity: 0.85;
}

/* 内容区 */
.profile-content {
  margin-top: -40px;
  padding: 0 12px 20px;
}
.card {
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: var(--shadow-paper);
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 14px;
  color: var(--ink);
  font-family: var(--font-serif);
}

/* 数据统计 */
.stats-card {
  display: flex;
  padding: 18px 0;
}
.stat-item {
  flex: 1;
  text-align: center;
}
.stat-item + .stat-item {
  border-left: 1px solid var(--paper-line);
}
.stat-num {
  font-size: 20px;
  font-weight: 600;
  color: var(--stamp-red);
  font-family: var(--font-serif);
}
.stat-label {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-text-color-3);
}

/* 服务宫格 */
.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink);
  transition: transform var(--duration-stamp) ease;
}
.service-item:active {
  transform: scale(0.97);
}
.service-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 菜单 */
.menu-card {
  padding: 4px 0;
  overflow: hidden;
}
.menu-card :deep(.van-cell) {
  padding: 14px 16px;
}
.menu-icon {
  margin-right: 10px;
}
.menu-title {
  position: relative;
  display: inline-flex;
  align-items: center;
}
.red-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--stamp-red);
  margin-left: 6px;
}

/* 退出登录 */
.logout-btn {
  margin-top: 8px;
  color: var(--stamp-red);
  background: var(--paper-white);
  border: 2px solid var(--stamp-red);
  transition: transform var(--duration-stamp) ease;
}
.logout-btn:active {
  transform: scale(0.98);
}

/* 弹窗 */
.popup-title {
  padding: 20px 16px 12px;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
  color: var(--ink);
  font-family: var(--font-serif);
}
.popup-actions {
  padding: 20px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
}
</style>
