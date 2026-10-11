<template>
  <div class="page">
    <van-nav-bar title="我的收藏" left-arrow @click-left="onBack" safe-area-inset-top />

    <div class="content">
      <van-empty v-if="favorites.length === 0" description="还没有收藏景点">
        <template #image>
          <van-icon name="star-o" size="56" color="#dcdee0" />
        </template>
        <van-button type="primary" round size="small" @click="router.push('/')">
          去发现景点
        </van-button>
      </van-empty>

      <van-swipe-cell v-for="fav in favorites" :key="fav.id">
        <div class="fav-card">
          <div class="fav-head">
            <div class="fav-name">{{ fav.name }}</div>
            <span class="city-tag">{{ fav.city }}</span>
          </div>
          <div class="fav-meta">
            <van-icon name="clock-o" />
            <span>{{ fav.duration || '时长待定' }}</span>
            <span class="sep">|</span>
            <van-icon name="gold-coin-o" />
            <span>{{ fav.cost || '费用待定' }}</span>
          </div>
          <div class="fav-desc">{{ fav.description }}</div>
          <div class="fav-time">收藏于 {{ formatTime(fav.createdAt) }}</div>
        </div>
        <template #right>
          <van-button square type="danger" text="取消收藏" class="swipe-btn" @click="removeFav(fav.id)" />
        </template>
      </van-swipe-cell>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { favoriteStore, formatTime } from '../utils/storage.js'

const router = useRouter()
const favorites = ref(favoriteStore.list())

const onBack = () => router.back()

const removeFav = (id) => {
  showConfirmDialog({ title: '提示', message: '确定取消收藏该景点吗？' })
    .then(() => {
      favoriteStore.remove(id)
      favorites.value = favoriteStore.list()
      showToast('已取消收藏')
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
.fav-card {
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: var(--shadow-paper);
}
.fav-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.fav-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
  font-family: var(--font-serif);
}
.city-tag {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--stamp-red);
  background: rgba(196, 71, 58, 0.07);
  border: 1px solid rgba(196, 71, 58, 0.4);
  border-radius: var(--radius-photo);
  padding: 2px 8px;
  font-family: var(--font-serif);
}
.fav-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--van-text-color-3);
}
.fav-meta .sep {
  margin: 0 4px;
}
.fav-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--ink);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fav-time {
  margin-top: 8px;
  font-size: 11px;
  color: var(--van-text-color-3);
}
.swipe-btn {
  height: 100%;
}
</style>
