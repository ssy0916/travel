<template>
  <div class="detail-container">
    <van-nav-bar
      :title="navTitle"
      left-arrow
      @click-left="onBack"
      safe-area-inset-top
    />

    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrap">
      <van-loading type="spinner" color="#1989fa">正在生成行程...</van-loading>
    </div>

    <!-- 生成失败（接口返回业务错误） -->
    <div v-else-if="loadError" class="error-wrap">
      <van-empty :description="errorMsg || '行程生成失败，请稍后重试'" />
      <van-button
        v-if="!isSavedMode"
        type="primary"
        round
        size="small"
        class="retry-btn"
        @click="retry"
      >
        重新生成
      </van-button>
    </div>

    <template v-else-if="itinerary">
      <!-- 城市风光横幅：图片与城市名一一对应（未收录城市用通用风光兜底） -->
      <div class="city-banner">
        <img :src="cityImage(itinerary.city)" :alt="`${itinerary.city}城市风光`" />
        <div class="city-banner-mask"></div>
        <div class="city-banner-copy">
          <span class="city-banner-name">{{ itinerary.city }}</span>
          <span class="city-banner-sub">
            {{ itinerary.day }}天行程 · 预算 ¥{{ itinerary.budget ?? '待生成' }}
          </span>
        </div>
      </div>

      <!-- 行程概览 -->
      <div class="summary-card">
        <div class="summary-left">
          <span class="city">{{ itinerary.city }}</span>
          <span class="dot">·</span>
          <span class="day">{{ itinerary.day }}天行程</span>
        </div>
        <div class="summary-right">
          预算：<span class="budget">{{ itinerary.budget }}元</span>
        </div>
      </div>
      <div v-if="itinerary.sourceFlags" class="source-summary">
        <span>行程：{{ llmSourceText(itinerary.sourceFlags.llm) }}</span>
        <span>地理编码：{{ sourceText(itinerary.sourceFlags.geocode) }}</span>
        <span>天气：{{ sourceText(itinerary.sourceFlags.weather) }}</span>
        <span>景点：{{ sourceText(itinerary.sourceFlags.poi) }}</span>
        <span>住宿：{{ sourceText(itinerary.sourceFlags.hotel) }}</span>
      </div>
      <a
        v-if="cityMapUrl"
        class="city-map-link"
        :href="cityMapUrl"
        target="_blank"
        rel="noopener noreferrer"
      >在高德地图查看城市位置</a>

      <!-- 降级提示条：后端规则模板或本地示例数据 -->
      <div v-if="itinerary.degraded" class="degrade-banner">
        <van-icon name="warning-o" size="16" class="degrade-icon" />
        <span class="degrade-text">{{ itinerary.degradeReason || '当前为降级行程，内容仅供参考' }}</span>
        <van-button
          v-if="!isSavedMode"
          size="mini"
          plain
          type="warning"
          class="degrade-retry"
          @click="retry"
        >
          重新生成
        </van-button>
      </div>

      <div v-if="itinerary.weather && itinerary.weather.length" class="card data-card">
        <div class="card-title">天气参考 <span class="data-source">高德天气</span></div>
        <div v-for="item in itinerary.weather" :key="item.date" class="data-line">
          <strong>{{ item.date }}</strong>
          <span>{{ item.dayWeather }} / {{ item.nightWeather }}</span>
          <span>{{ item.dayTemperature }}℃ / {{ item.nightTemperature }}℃</span>
        </div>
      </div>

      <!-- 每日行程（可折叠） -->
      <van-collapse
        v-if="itinerary.days && itinerary.days.length"
        v-model="activeNames"
        class="day-collapse"
      >
        <van-collapse-item
          v-for="d in itinerary.days"
          :key="d.day"
          :name="d.day"
          :title="d.title"
        >
          <div class="day-content">
            <div
              v-for="slot in (d.slots || [])"
              :key="slot.period"
              class="slot-block"
            >
              <div class="period">{{ slot.period }}</div>
              <template v-if="slot.attractions && slot.attractions.length">
                <div
                  v-for="(item, idx) in slot.attractions"
                  :key="idx"
                  class="attraction-card"
                >
                  <img v-if="item.photo" :src="item.photo" :alt="item.name" class="poi-photo">
                  <div class="attr-name-row">
                    <div class="attr-name">{{ item.name || '景点信息待生成' }}</div>
                    <van-icon
                      :name="isFaved(item) ? 'star' : 'star-o'"
                      :color="isFaved(item) ? '#ff976a' : '#c8c9cc'"
                      size="20"
                      class="fav-icon"
                      @click="toggleFav(item)"
                    />
                  </div>
                  <div v-if="item.duration || item.cost" class="attr-meta">
                    <template v-if="item.duration">
                      <van-icon name="clock-o" />
                      <span>{{ item.duration }}</span>
                    </template>
                    <template v-if="item.cost">
                      <span v-if="item.duration" class="dot-sep">|</span>
                      <van-icon name="gold-coin-o" />
                      <span>{{ item.cost }}</span>
                    </template>
                  </div>
                  <div v-if="item.transport" class="attr-meta">
                    <van-icon name="location-o" />
                    <span>{{ item.transport }}</span>
                  </div>
                  <div v-if="item.ticket || item.openTime" class="attr-meta">
                    <span v-if="item.ticket">门票：{{ item.ticket }}</span>
                    <span v-if="item.ticket && item.openTime" class="dot-sep">|</span>
                    <span v-if="item.openTime">开放：{{ item.openTime }}</span>
                  </div>
                  <div v-if="item.address" class="attr-meta">
                    <van-icon name="location-o" />
                    <span>{{ item.address }}</span>
                  </div>
                  <div v-if="item.tips" class="attr-meta">{{ item.tips }}</div>
                  <div v-if="item.poiVerification === 'unverified'" class="poi-status">
                    景点信息尚未通过高德 POI 校验
                  </div>
                  <a
                    v-if="mapUrl(item)"
                    class="map-link"
                    :href="mapUrl(item)"
                    target="_blank"
                    rel="noopener noreferrer"
                  >在高德地图查看</a>
                  <div v-if="item.description" class="attr-desc">{{ item.description }}</div>
                </div>
              </template>
              <div v-else class="slot-empty">景点信息待生成</div>
            </div>
          </div>
        </van-collapse-item>
      </van-collapse>
      <div v-else class="card empty-card">每日行程待生成，请稍后重新生成</div>

      <div
        v-if="itinerary.hotelRecommendations && itinerary.hotelRecommendations.length"
        class="card data-card"
      >
        <div class="card-title">附近住宿 POI <span class="data-source">高德搜索结果</span></div>
        <div v-for="hotel in itinerary.hotelRecommendations" :key="hotel.name" class="hotel-row">
          <img v-if="hotel.photo" :src="hotel.photo" :alt="hotel.name" class="hotel-photo">
          <div class="hotel-info">
            <strong>{{ hotel.name }}</strong>
            <span v-if="hotel.address">{{ hotel.address }}</span>
            <span v-if="hotel.rating">评分：{{ hotel.rating }}</span>
            <span v-if="hotel.price">高德标注价格：{{ hotel.price }}（仅供参考）</span>
          </div>
        </div>
        <div class="data-note">仅为 POI 搜索结果，不代表价格、空房或质量保证。</div>
      </div>

      <!-- 预算明细（缺失项显示“待生成”） -->
      <div v-if="budgetRows.length" class="card budget-card">
        <div class="card-title">预算明细</div>
        <div
          v-for="row in budgetRows"
          :key="row.key"
          class="budget-row"
          :class="{ total: row.key === 'total' }"
        >
          <span>{{ row.label }}</span>
          <span :class="{ 'total-price': row.key === 'total' }">{{ row.value == null ? '待生成' : '¥' + row.value }}</span>
        </div>
      </div>

      <!-- 温馨提示 -->
      <div v-if="itinerary.tips && itinerary.tips.length" class="card tips-card">
        <div class="card-title">温馨提示</div>
        <ul class="tips-list">
          <li v-for="(tip, i) in itinerary.tips" :key="i">{{ tip }}</li>
        </ul>
      </div>

      <!-- 我的备注（已保存行程可编辑写回） -->
      <div v-if="isSavedMode" class="card note-card">
        <div class="card-title note-title">
          我的备注
          <van-icon name="edit" class="note-edit" @click="openNoteDialog" />
        </div>
        <div class="note-content">{{ tripNote || '暂无备注，点击右上角图标添加' }}</div>
      </div>

      <!-- 底部操作栏 -->
      <div class="bottom-bar">
        <van-button
          v-if="!isSavedMode"
          round
          size="large"
          class="save-btn"
          :icon="tripSaved ? 'success' : 'star-o'"
          :disabled="tripSaved"
          @click="saveTrip"
        >
          {{ tripSaved ? '已保存' : '保存行程' }}
        </van-button>
        <van-button
          v-else
          round
          size="large"
          class="save-btn"
          icon="edit"
          @click="openNoteDialog"
        >
          编辑备注
        </van-button>
        <van-button type="primary" block round size="large" @click="onConsultAI">
          咨询 AI 助手
        </van-button>
      </div>

      <van-dialog
        v-model:show="showNoteDialog"
        title="编辑备注"
        show-cancel-button
        @confirm="saveNote"
      >
        <van-field
          v-model="noteDraft"
          type="textarea"
          rows="3"
          maxlength="500"
          show-word-limit
          placeholder="写点什么，比如出行注意事项..."
        />
      </van-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { generateItinerary } from '../api/itinerary'
import mockItinerary from '../mock/itinerary.js'
import { tripStore, favoriteStore, historyStore } from '../utils/storage.js'
import { cityImage } from '../utils/cityImage.js'

const route = useRoute()
const router = useRouter()

// 通过 ?id=xxx 打开时为“查看已保存行程”模式，不再请求生成接口
const isSavedMode = computed(() => !!route.query.id)

const loading = ref(true)
const itinerary = ref(null)
const loadError = ref(false) // 接口返回业务失败（success=false）
const errorMsg = ref('')
const activeNames = ref([1]) // 默认展开第1天
const tripSaved = ref(false)
const favTick = ref(0) // 收藏状态变更后触发视图刷新

// 已保存行程的备注编辑
const tripNote = ref('')
const showNoteDialog = ref(false)
const noteDraft = ref('')
const tripId = computed(() => route.query.id)

// 预算明细按固定顺序渲染；字段缺失（降级模板）时 value 为 null，显示“待生成”
const budgetRows = computed(() => {
  const b = itinerary.value?.budgetBreakdown
  if (!b) return []
  return [
    { key: 'accommodation', label: '住宿' },
    { key: 'dining', label: '餐饮' },
    { key: 'transport', label: '交通' },
    { key: 'tickets', label: '门票' },
    { key: 'other', label: '其他' },
    { key: 'total', label: '总计' }
  ].map((row) => ({ ...row, value: b[row.key] }))
})

const navTitle = computed(() => `${route.query.city || itinerary.value?.city || ''}行程规划`)

const sourceText = (source) => {
  if (source === 'real') return '已查询'
  if (source === 'fallback') return '未配置或不可用'
  if (source === 'mock') return '本地示例'
  return '未提供'
}

const llmSourceText = (source) => {
  if (source === 'real') return '大模型生成'
  if (source === 'fallback') return '规则模板'
  if (source === 'mock') return '本地示例'
  return '未提供'
}

const cityMapUrl = computed(() => {
  const { centerLng, centerLat, city } = itinerary.value || {}
  if (centerLng == null || centerLat == null) return ''
  return `https://uri.amap.com/marker?position=${centerLng},${centerLat}&name=${encodeURIComponent(city || '')}`
})

const mapUrl = (item) => {
  if (item.lng == null || item.lat == null) return ''
  return `https://uri.amap.com/marker?position=${item.lng},${item.lat}&name=${encodeURIComponent(item.name || '')}`
}

const formData = ref({
  city: route.query.city || '',
  budget: route.query.budget || '',
  day: route.query.day || ''
})

onMounted(() => {
  loadItinerary()
})

const loadItinerary = async () => {
  // 已保存行程：从后端读取详情
  if (isSavedMode.value) {
    try {
      const trip = await tripStore.get(route.query.id)
      if (!trip || !trip.data) {
        showToast('行程不存在或已被删除')
        router.replace('/trips')
        return
      }
      itinerary.value = trip.data
      tripNote.value = trip.note || ''
      tripSaved.value = true
    } catch {
      showToast('行程不存在或已被删除')
      router.replace('/trips')
      return
    } finally {
      loading.value = false
    }
    return
  }

  loading.value = true
  loadError.value = false
  errorMsg.value = ''
  try {
    const { data } = await generateItinerary({
      city: formData.value.city,
      budget: Number(formData.value.budget),
      day: Number(formData.value.day)
    })
    if (data && data.success === false) {
      // 后端明确业务失败：展示错误态 + 重试按钮
      // （未配 Key / LLM 失败等场景后端会返回 success=true + degraded=true 的降级模板，不走这里）
      loadError.value = true
      errorMsg.value = data.error || '行程生成失败，请稍后重试'
      return
    }
    itinerary.value = data
  } catch (e) {
    // 网络不可达 / 后端异常：兜底本地示例数据（mock 自带 degraded 标识，不会伪装实时数据）
    console.warn('行程接口调用失败，使用本地 Mock 数据：', e.message)
    itinerary.value = mockItinerary
    showToast('网络异常，已加载本地示例数据')
  } finally {
    loading.value = false
    // 记录浏览历史（仅生成模式且拿到行程数据）
    if (itinerary.value) {
      historyStore.add({
        city: itinerary.value.city,
        budget: itinerary.value.budget,
        day: itinerary.value.day
      })
    }
  }
}

// 降级/失败后重新生成（已保存行程模式不适用）
const retry = () => {
  if (isSavedMode.value) return
  itinerary.value = null
  loadItinerary()
}

const saveTrip = async () => {
  if (!itinerary.value) return
  try {
    const res = await tripStore.add(itinerary.value)
    tripSaved.value = true
    showToast(res.duplicated ? '该行程已保存过，可在「我的行程」中查看' : '行程已保存，可在「我的行程」中查看')
  } catch {
    // 拦截器已提示
  }
}

// 备注编辑写回
const openNoteDialog = () => {
  noteDraft.value = tripNote.value
  showNoteDialog.value = true
}

const saveNote = async () => {
  try {
    await tripStore.updateNote(route.query.id, noteDraft.value)
    tripNote.value = noteDraft.value
    showToast('备注已保存')
  } catch {
    // 拦截器已提示
  }
}

const isFaved = (item) => {
  favTick.value // 建立响应式依赖
  return favoriteStore.has(itinerary.value.city, item.name)
}

const toggleFav = (item) => {
  const city = itinerary.value.city
  if (favoriteStore.has(city, item.name)) {
    favoriteStore.removeByCityName(city, item.name)
    showToast('已取消收藏')
  } else {
    favoriteStore.add({
      name: item.name,
      city,
      duration: item.duration,
      cost: item.cost,
      description: item.description
    })
    showToast('已收藏，可在「我的收藏」中查看')
  }
  favTick.value++
}

const onBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

const onConsultAI = () => {
  router.push('/chat')
}
</script>

<style scoped>
.detail-container {
  min-height: 100vh;
  background-color: #f5f5f5;
  padding-bottom: 80px;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 80px 0;
}

/* 失败态 */
.error-wrap {
  padding: 40px 0 20px;
  text-align: center;
}
.retry-btn {
  margin-top: -8px;
}

/* 降级提示条 */
.degrade-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 12px 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff7e8;
  border: 1px solid #ffe0a3;
  color: #ab6b00;
  font-size: 12px;
  line-height: 1.5;
}
.degrade-icon {
  flex-shrink: 0;
}
.degrade-text {
  flex: 1;
  min-width: 0;
}
.degrade-retry {
  flex-shrink: 0;
}
.source-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin: 0 16px 8px;
  color: #777;
  font-size: 11px;
}
.city-map-link {
  display: inline-block;
  margin: 0 16px 12px;
  color: #1989fa;
  font-size: 12px;
}
.data-card {
  margin-top: 0;
}
.data-source,
.poi-status,
.data-note {
  color: #969799;
  font-size: 12px;
  font-weight: normal;
}
.data-line,
.hotel-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
  color: #646566;
  border-top: 1px solid #f2f3f5;
}
.hotel-row {
  align-items: flex-start;
}
.hotel-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.hotel-info strong {
  color: #323233;
}
.hotel-photo {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
  flex-shrink: 0;
}
.data-note {
  line-height: 1.5;
}
.poi-photo {
  display: block;
  width: 100%;
  max-height: 180px;
  object-fit: cover;
  border-radius: 6px;
  margin-bottom: 10px;
}
.poi-status {
  margin-top: 4px;
}
.map-link {
  display: inline-block;
  margin-top: 4px;
  color: #1989fa;
  font-size: 12px;
}

/* 空状态 */
.empty-card {
  text-align: center;
  color: #969799;
  font-size: 13px;
}

/* 我的备注 */
.note-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.note-edit {
  color: #1989fa;
  cursor: pointer;
}
.note-content {
  margin-top: 8px;
  font-size: 13px;
  color: #646566;
  line-height: 1.6;
  white-space: pre-wrap;
}
.slot-empty {
  padding: 12px;
  margin-bottom: 8px;
  background: #f7f8fa;
  border-radius: 8px;
  color: #969799;
  font-size: 13px;
  text-align: center;
}

/* 概览卡片 */
/* 城市风光横幅 */
.city-banner {
  position: relative;
  height: 180px;
  margin: 12px;
  overflow: hidden;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  background: #d8dde3;
}

.city-banner img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.city-banner-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 38%, rgba(0, 0, 0, 0.62) 100%);
}

.city-banner-copy {
  position: absolute;
  right: 16px;
  bottom: 13px;
  left: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #fff;
}

.city-banner-name {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 3px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
}

.city-banner-sub {
  font-size: 12px;
  letter-spacing: 0.5px;
  opacity: 0.92;
}

.summary-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px;
  padding: 16px;
  border-radius: 10px;
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: #fff;
}
.summary-left {
  font-size: 16px;
  font-weight: 600;
}
.summary-left .dot {
  margin: 0 6px;
  opacity: 0.8;
}
.summary-right {
  font-size: 14px;
}
.summary-right .budget {
  font-weight: 600;
  font-size: 16px;
}

/* 每日折叠 */
.day-collapse {
  margin: 0 12px 12px;
  border-radius: 10px;
  overflow: hidden;
}
.day-content {
  padding: 4px 0;
}
.slot-block {
  margin-bottom: 12px;
}
.period {
  font-size: 14px;
  font-weight: 600;
  color: #1989fa;
  margin: 8px 0 6px;
  padding-left: 4px;
  border-left: 3px solid #1989fa;
}
.attraction-card {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}
.attr-name-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.attr-name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 6px;
}
.fav-icon {
  flex-shrink: 0;
  padding: 2px;
}
.attr-meta {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #969799;
  gap: 4px;
  margin-bottom: 4px;
}
.attr-meta .dot-sep {
  margin: 0 4px;
}
.attr-desc {
  font-size: 13px;
  color: #646566;
  line-height: 1.6;
  margin-top: 6px;
}

/* 通用卡片 */
.card {
  background: #fff;
  margin: 0 12px 12px;
  border-radius: 10px;
  padding: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 12px;
}

/* 预算明细 */
.budget-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
  color: #646566;
  border-bottom: 1px solid #f2f3f5;
}
.budget-row:last-child {
  border-bottom: none;
}
.budget-row.total {
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid #ebedf0;
  border-bottom: none;
  font-weight: 600;
  color: #323233;
}
.total-price {
  color: #ee0a24;
  font-size: 16px;
}

/* 温馨提示 */
.tips-list {
  margin: 0;
  padding-left: 20px;
}
.tips-list li {
  font-size: 13px;
  color: #646566;
  line-height: 1.8;
}

/* 底部按钮 */
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 10px;
  padding: 10px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
  z-index: 100;
}
.save-btn {
  flex-shrink: 0;
  color: #ff976a;
  border-color: #ffd9bf;
}
</style>
