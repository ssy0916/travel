<template>
  <div class="page">
    <van-nav-bar title="浏览历史" left-arrow @click-left="onBack" safe-area-inset-top>
      <template #right>
        <span v-if="historyList.length" class="clear-btn" @click="clearAll">清空</span>
      </template>
    </van-nav-bar>

    <div class="content">
      <van-empty v-if="historyList.length === 0" description="还没有浏览记录">
        <template #image>
          <van-icon name="clock-o" size="56" color="#dcdee0" />
        </template>
        <van-button type="primary" round size="small" @click="router.push('/')">
          去规划行程
        </van-button>
      </van-empty>

      <div
        v-for="item in historyList"
        :key="item.id"
        class="history-card"
        @click="replan(item)"
      >
        <img
          class="city-thumb"
          :src="cityImage(item.city)"
          :alt="`${item.city}城市风光`"
          loading="lazy"
        />
        <div class="info">
          <div class="city">{{ item.city }}</div>
          <div class="meta">
            {{ item.day }}天 · 预算 ¥{{ item.budget }} · {{ formatTime(item.viewedAt) }}
          </div>
        </div>
        <van-icon name="replay" size="18" color="#c8c9cc" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { historyStore, formatTime } from '../utils/storage.js'
import { cityImage } from '../utils/cityImage.js'

const router = useRouter()
const historyList = ref(historyStore.list())

const onBack = () => router.back()

const replan = (item) => {
  router.push({
    path: '/detail',
    query: { city: item.city, budget: item.budget, day: item.day }
  })
}

const clearAll = () => {
  showConfirmDialog({ title: '提示', message: '确定清空全部浏览历史吗？' })
    .then(() => {
      historyStore.clear()
      historyList.value = []
      showToast('已清空')
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
.clear-btn {
  font-size: 14px;
  color: var(--stamp-red);
}
.history-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: var(--shadow-paper);
  transition: transform var(--duration-stamp) ease;
}
.history-card:active {
  transform: scale(0.97);
}
.city-thumb {
  width: 54px;
  height: 54px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(74, 59, 39, 0.14);
  background: var(--paper-line);
}
.info {
  flex: 1;
  min-width: 0;
}
.city {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
  font-family: var(--font-serif);
}
.meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--van-text-color-3);
}
</style>
