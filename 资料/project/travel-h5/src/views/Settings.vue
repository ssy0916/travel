<template>
  <div class="page">
    <van-nav-bar title="设置" left-arrow @click-left="onBack" safe-area-inset-top />

    <div class="content">
      <div class="page-kicker">
        <span class="postage-stamp">旅</span>
        <span class="kicker-text">PREFERENCES · 手账设置</span>
      </div>

      <div class="card">
        <van-cell center title="消息通知" icon="bell">
          <template #label>
            <span class="cell-label">接收行程提醒与活动通知</span>
          </template>
          <template #right-icon>
            <van-switch v-model="noticeEnabled" size="22" @change="onNoticeChange" />
          </template>
        </van-cell>
        <van-cell title="清除缓存" is-link icon="delete-o" @click="clearCache">
          <template #value>
            <span class="cache-size">{{ cacheSize }}</span>
          </template>
        </van-cell>
      </div>

      <div class="card">
        <van-cell title="检查新版本" is-link icon="upgrade" @click="checkVersion">
          <template #value>
            <span class="cell-label">v1.0.0</span>
          </template>
        </van-cell>
        <van-cell title="关于我们" is-link icon="bookmark-o" @click="showAbout" />
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
      <van-button
        v-else
        block
        round
        type="primary"
        class="logout-btn"
        @click="router.push('/profile')"
      >
        去登录
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showDialog } from 'vant'
import { settingsStore, userStore } from '../utils/storage.js'

const router = useRouter()
const isLogin = ref(!!userStore.get())
const noticeEnabled = ref(settingsStore.get().noticeEnabled)

/** 统计非业务数据（非 travel_ 前缀）占用，业务数据不参与清理 */
const calcCacheSize = () => {
  let bytes = 0
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key.startsWith('travel_')) {
      bytes += key.length + (localStorage.getItem(key) || '').length
    }
  }
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`
}
const cacheSize = ref(calcCacheSize())

const onBack = () => router.back()

const onNoticeChange = (val) => {
  settingsStore.set('noticeEnabled', val)
  showToast(val ? '已开启消息通知' : '已关闭消息通知')
}

const clearCache = () => {
  showConfirmDialog({
    title: '清除缓存',
    message: '将清理本地临时缓存，行程、收藏等个人数据不受影响，确定继续吗？'
  })
    .then(() => {
      Object.keys(localStorage)
        .filter((key) => !key.startsWith('travel_'))
        .forEach((key) => localStorage.removeItem(key))
      cacheSize.value = calcCacheSize()
      showToast('缓存已清理')
    })
    .catch(() => {})
}

const checkVersion = () => {
  showToast('当前已是最新版本')
}

const showAbout = () => {
  showDialog({
    title: '关于智能旅游助手',
    message:
      '智能旅游助手 v1.0.0\n基于 AI 的智能景点介绍与行程规划系统，输入目的地、预算和天数，即可一键生成专属旅行方案。',
    confirmButtonText: '知道了'
  })
}

const onLogout = () => {
  showConfirmDialog({ title: '提示', message: '确定要退出登录吗？' })
    .then(() => {
      userStore.clear()
      isLogin.value = false
      showToast('已退出登录')
      router.push('/profile')
    })
    .catch(() => {})
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: var(--paper-bg);
}
.content {
  padding: 12px;
}

/* 手账眉批：邮票 + 栏目名 */
.page-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 2px 4px 10px;
}

.postage-stamp {
  display: grid;
  width: 24px;
  height: 30px;
  place-content: center;
  border: 1px dashed rgba(196, 71, 58, 0.65);
  border-radius: 2px;
  background: rgba(196, 71, 58, 0.05);
  color: var(--stamp-red);
  font-family: var(--font-serif);
  font-size: 13px;
  transform: rotate(5deg);
}

.kicker-text {
  font-family: var(--font-serif);
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--van-text-color-3);
}

/* 卡片内 cell 左侧图标统一邮戳红 */
.card :deep(.van-cell__left-icon) {
  color: var(--stamp-red);
}
.card {
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  margin-bottom: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-paper);
}
.cell-label {
  font-size: 12px;
  color: var(--van-text-color-3);
}
.cache-size {
  font-size: 13px;
  color: var(--van-text-color-3);
  margin-right: 4px;
}
.logout-btn {
  margin-top: 8px;
  color: var(--stamp-red);
  background: var(--paper-white);
  border: 2px solid var(--stamp-red);
  font-family: var(--font-serif);
  font-weight: bold;
  letter-spacing: 2px;
  box-shadow: var(--shadow-stamp);
  transition: transform var(--duration-stamp) ease;
}
.logout-btn:active {
  transform: scale(0.98);
}
</style>
