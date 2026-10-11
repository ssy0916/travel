<template>
  <div class="page">
    <van-nav-bar title="消息通知" left-arrow @click-left="onBack" safe-area-inset-top>
      <template #right>
        <span v-if="unread > 0" class="read-all" @click="readAll">全部已读</span>
      </template>
    </van-nav-bar>

    <div class="content">
      <van-empty v-if="notices.length === 0" description="暂无消息通知" />

      <div
        v-for="n in notices"
        :key="n.id"
        class="notice-card"
        :class="{ unread: !n.read }"
        @click="onRead(n)"
      >
        <div class="icon-box" :style="{ background: typeMap[n.type].bg }">
          <van-icon :name="typeMap[n.type].icon" size="20" :color="typeMap[n.type].color" />
        </div>
        <div class="notice-body">
          <div class="notice-head">
            <span class="title">{{ n.title }}</span>
            <span v-if="!n.read" class="dot"></span>
          </div>
          <div class="notice-content">{{ n.content }}</div>
          <div class="notice-time">{{ formatTime(n.time) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { noticeStore, formatTime } from '../utils/storage.js'

const router = useRouter()
const notices = ref(noticeStore.list())
const unread = computed(() => notices.value.filter((n) => !n.read).length)

const typeMap = {
  system: { icon: 'info-o', color: '#2F7F7A', bg: 'rgba(47,127,122,0.1)' },
  trip: { icon: 'location-o', color: '#C4473A', bg: 'rgba(196,71,58,0.08)' },
  promo: { icon: 'gift-o', color: '#D9A441', bg: 'rgba(217,164,65,0.12)' }
}

const onBack = () => router.back()

const onRead = (n) => {
  if (n.read) return
  noticeStore.markRead(n.id)
  notices.value = noticeStore.list()
}

const readAll = () => {
  noticeStore.markAllRead()
  notices.value = noticeStore.list()
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
.read-all {
  font-size: 14px;
  color: var(--stamp-red);
}
.notice-card {
  display: flex;
  gap: 12px;
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: var(--shadow-paper);
  transition: transform var(--duration-stamp) ease;
}
.notice-card:active {
  transform: scale(0.97);
}
.notice-card.unread {
  background: #fbf3e4;
}
.icon-box {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.notice-body {
  flex: 1;
  min-width: 0;
}
.notice-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.title {
  font-size: 15px;
  color: var(--van-text-color-2, #5a5245);
}
.unread .title {
  font-weight: 600;
  color: var(--ink);
  font-family: var(--font-serif);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--stamp-red);
  flex-shrink: 0;
}
.notice-content {
  margin-top: 6px;
  font-size: 13px;
  color: var(--van-text-color-3);
  line-height: 1.6;
}
.notice-time {
  margin-top: 8px;
  font-size: 11px;
  color: var(--van-text-color-3);
}
</style>
