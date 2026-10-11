<template>
  <div class="page">
    <van-nav-bar title="我的行程" left-arrow @click-left="onBack" safe-area-inset-top class="journal-nav" />

    <!-- 城市筛选 -->
    <van-search
      v-model="cityKeyword"
      placeholder="按城市筛选"
      shape="round"
      background="transparent"
      @search="onSearch"
      @clear="onSearch"
    />

    <div class="content">
      <van-list
        v-model:loading="listLoading"
        :finished="finished"
        finished-text="—— 手帐翻到最后一页 ——"
        @load="onLoad"
      >
        <van-empty v-if="!listLoading && trips.length === 0" description="手帐还是空的，去写下第一页吧">
          <van-button type="primary" round size="small" @click="router.push('/')">
            去规划行程
          </van-button>
        </van-empty>

        <van-swipe-cell v-for="trip in trips" :key="trip.id">
          <div class="trip-card ticket-card" @click="openTrip(trip.id)">
            <img
              class="trip-thumb"
              :src="cityImage(trip.city)"
              :alt="`${trip.city}城市风光`"
              loading="lazy"
            />
            <div class="trip-info">
              <div class="trip-main">
                <div class="trip-city">
                  <span class="stamp">{{ trip.city }}</span>
                  <span v-if="trip.degraded" class="degraded-tag">降级</span>
                </div>
                <van-icon name="arrow" color="#C4473A" />
              </div>
              <div class="ticket-divider"></div>
              <div class="trip-meta">
                <span>{{ trip.day }}天行程</span>
                <span class="sep">·</span>
                <span>预算 ¥{{ trip.budget ?? '待生成' }}</span>
                <span class="sep">·</span>
                <span>{{ formatTime(trip.createdAt) }}</span>
              </div>
              <div v-if="trip.note" class="trip-note handwritten-note">{{ trip.note }}</div>
            </div>
          </div>
          <template #right>
            <van-button square type="danger" text="删除" class="swipe-btn" @click="removeTrip(trip.id)" />
          </template>
        </van-swipe-cell>
      </van-list>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { tripStore, formatTime } from '../utils/storage.js'
import { cityImage } from '../utils/cityImage.js'

const router = useRouter()
const trips = ref([])
const cityKeyword = ref('')
const page = ref(0)
const size = 10
const listLoading = ref(false)
const finished = ref(false)

const onBack = () => router.back()

const openTrip = (id) => {
  router.push({ path: '/detail', query: { id } })
}

const fetchPage = async (reset = false) => {
  if (reset) {
    page.value = 0
    trips.value = []
    finished.value = false
  }
  listLoading.value = true
  try {
    const res = await tripStore.list({
      page: page.value,
      size,
      city: cityKeyword.value || undefined
    })
    trips.value = reset ? res.list : trips.value.concat(res.list)
    finished.value = !res.hasMore
    page.value += 1
  } catch (e) {
    finished.value = true
  } finally {
    listLoading.value = false
  }
}

const onLoad = () => fetchPage(false)

const onSearch = () => fetchPage(true)

const removeTrip = (id) => {
  showConfirmDialog({ title: '提示', message: '确定删除这条行程吗？' })
    .then(async () => {
      await tripStore.remove(id)
      trips.value = trips.value.filter((t) => t.id !== id)
      showToast('已删除')
    })
    .catch(() => {})
}

onMounted(async () => {
  // 旧 localStorage 行程一次性迁移到后端
  const migrated = await tripStore.migrate()
  if (migrated > 0) showToast(`已迁移 ${migrated} 条本地行程到服务端`)
  fetchPage(true)
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: var(--paper-bg);
}
.content {
  padding: 12px;
}
.trip-card {
  display: flex;
  gap: 13px;
  align-items: stretch;
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: var(--shadow-paper);
  position: relative;
}
.trip-thumb {
  flex: none;
  align-self: center;
  width: 78px;
  height: 78px;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: 0 2px 6px rgba(74, 59, 39, 0.14);
  background: var(--paper-line);
}
.trip-info {
  flex: 1;
  min-width: 0;
}
.trip-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.trip-city {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
  font-family: var(--font-serif);
}
.degraded-tag {
  font-size: 10px;
  color: var(--bronze);
  background: rgba(217, 164, 65, 0.1);
  border: 1px solid rgba(217, 164, 65, 0.3);
  border-radius: 4px;
  padding: 0 4px;
}
.ticket-divider {
  border-top: 1px dashed var(--paper-line);
  margin: 10px 0;
}
.trip-meta {
  font-size: 12px;
  color: #969799;
}
.trip-meta .sep {
  margin: 0 6px;
}
.trip-note {
  margin-top: 8px;
  padding: 6px 8px;
}
.swipe-btn {
  height: 100%;
}
</style>
