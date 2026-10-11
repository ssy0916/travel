<template>
  <div class="page-container">
    <header class="hero">
      <Transition name="hero-fade">
        <img
          :key="heroImage"
          class="hero-image"
          :src="heroImage"
          :alt="heroAlt"
        />
      </Transition>
      <div class="hero-shade"></div>
      <div class="hero-content">
        <div class="hero-brand">
          <span class="brand-mark">旅</span>
          <span>漫游 · TRAVEL JOURNAL</span>
        </div>
        <p class="hero-kicker">把日子交给远方</p>
        <h1>下一站，<br />去哪里？</h1>
        <p class="hero-caption">灵感已经写好，只差一个出发的理由。</p>
        <div class="hero-stamp">出发<br />探索</div>
      </div>
      <div class="hero-edge"></div>
    </header>

    <main class="page-content">
      <section class="planner">
        <div class="planner-heading">
          <div>
            <span class="eyebrow">YOUR NEXT JOURNEY</span>
            <h2>规划一场旅行</h2>
          </div>
          <span class="planner-icon"><van-icon name="guide-o" /></span>
        </div>

        <button class="destination-picker" type="button" @click="showCityPicker = true">
          <span class="destination-icon"><van-icon name="location-o" /></span>
          <span class="destination-copy">
            <span class="field-label">目的地</span>
            <span class="destination-value" :class="{ placeholder: !formData.city }">
              {{ formData.city || '选择一座心动的城市' }}
            </span>
          </span>
          <van-icon name="arrow" class="destination-arrow" />
        </button>

        <div class="quick-picks" aria-label="热门城市快捷选择">
          <button
            v-for="city in quickCities"
            :key="city"
            type="button"
            class="quick-pick"
            :class="{ selected: formData.city === city }"
            @click="pickHotCity(city)"
          >
            {{ city }}
          </button>
          <button type="button" class="more-cities" @click="showCityPicker = true">
            更多 <van-icon name="arrow" />
          </button>
        </div>

        <div class="trip-fields">
          <label class="trip-field">
            <span class="field-label">旅行预算</span>
            <span class="input-line">
              <span class="input-prefix">¥</span>
              <input
                v-model="formData.budget"
                type="number"
                min="1"
                inputmode="numeric"
                placeholder="输入金额"
                aria-label="旅行预算金额"
              />
              <span class="input-suffix">元</span>
            </span>
          </label>
          <label class="trip-field">
            <span class="field-label">旅行天数</span>
            <span class="input-line">
              <input
                v-model="formData.day"
                type="number"
                min="1"
                inputmode="numeric"
                placeholder="几天"
                aria-label="旅行天数"
              />
              <span class="input-suffix">天</span>
            </span>
          </label>
        </div>

        <van-button
          type="primary"
          block
          round
          size="large"
          class="submit-btn"
          @click="handleSubmit"
        >
          开始规划 <van-icon name="arrow" />
        </van-button>
        <p class="planner-note"><van-icon name="checked" /> 专属行程 · 灵感即刻抵达</p>
      </section>

      <section class="destinations-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">FIND YOUR INSPIRATION</span>
            <h2>此刻热门目的地</h2>
          </div>
          <span class="section-note">挑一座城，去走走</span>
        </div>
        <div class="destination-grid">
          <div
            v-for="destination in destinations"
            :key="destination.name"
            class="destination-card"
          >
            <button
              type="button"
              class="destination-select"
              :aria-label="`选择${destination.name}：${destination.description}`"
              @click="pickHotCity(destination.name)"
            >
              <img
                :src="cityImage(destination.name)"
                :alt="`${destination.name}${destination.landmark}风光`"
                loading="lazy"
              />
              <span class="destination-overlay"></span>
              <span class="destination-card-copy">
                <span class="destination-name">{{ destination.name }}</span>
                <span class="destination-description">{{ destination.description }}</span>
              </span>
              <span v-if="formData.city === destination.name" class="selected-mark">
                <van-icon name="success" />
              </span>
            </button>
          </div>
        </div>
      </section>

      <section class="shortcuts">
        <button class="shortcut-item" type="button" @click="goChat">
          <span class="shortcut-icon chat-icon"><van-icon name="chat-o" /></span>
          <span><strong>问问旅行灵感</strong><small>和 AI 聊聊你的下一站</small></span>
          <van-icon name="arrow" class="shortcut-arrow" />
        </button>
        <button class="shortcut-item" type="button" @click="goProfile">
          <span class="shortcut-icon profile-icon"><van-icon name="orders-o" /></span>
          <span><strong>我的旅行手帐</strong><small>收藏与旅程都在这里</small></span>
          <van-icon name="arrow" class="shortcut-arrow" />
        </button>
      </section>
    </main>

    <van-popup
      round
      v-model:show="showCityPicker"
      position="bottom"
    >
      <van-picker
        title="选择城市"
        :columns="cityColumns"
        @confirm="onConfirm"
        @cancel="onCancel"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import cityColumns from '../data/cityData.js'
import { cityImage } from '../utils/cityImage.js'

const router = useRouter()
const showCityPicker = ref(false)

const formData = reactive({
  city: '',
  budget: null,
  day: null
})

// 选中的纯城市名：选择器返回“省 / 市”，快捷选择直接是城市名
const selectedCityName = computed(() => {
  if (!formData.city) return ''
  return formData.city.includes(' / ') ? formData.city.split(' / ')[1] : formData.city
})

// 顶部大图随城市切换；未选城市时使用通用旅行风光图
const heroImage = computed(() => cityImage(selectedCityName.value))
const heroAlt = computed(() =>
  selectedCityName.value ? `${selectedCityName.value}城市风光` : '旅行山水风光'
)

const quickCities = ['北京', '上海', '广州', '深圳', '成都', '杭州']
// 热门目的地：图片取自本地“城市名→风光图”确定性映射（见 utils/cityImage.js）
const destinations = [
  { name: '北京', landmark: '慕田峪长城', description: '慕田峪长城 · 古都漫步' },
  { name: '上海', landmark: '外滩天际线', description: '外滩万国建筑 · 浦江夜色' },
  { name: '广州', landmark: '广州塔', description: '广州塔夜景 · 岭南早茶' },
  { name: '深圳', landmark: '深圳城市天际线', description: '摩天城市 · 海岸活力' },
  { name: '成都', landmark: '成都大熊猫繁育研究基地', description: '熊猫基地 · 川味烟火' },
  { name: '杭州', landmark: '西湖', description: '西湖烟雨 · 苏堤漫步' },
  { name: '西安', landmark: '兵马俑', description: '兵马俑阵 · 古城夜游' },
  { name: '重庆', landmark: '洪崖洞', description: '洪崖洞夜景 · 山城轻轨' }
]

const onConfirm = ({ selectedOptions }) => {
  formData.city = selectedOptions
    .map(item => (typeof item === 'string' ? item : item.text))
    .join(' / ')
  showCityPicker.value = false
}
const onCancel = () => {
  showCityPicker.value = false
}

const pickHotCity = (city) => {
  formData.city = city
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const goChat = () => router.push('/chat')
const goProfile = () => router.push('/profile')

const handleSubmit = () => {
  if (!formData.city) {
    return showToast('请选择目的地')
  }
  if (!formData.budget) {
    return showToast('请输入预算金额')
  }
  if (!formData.day) {
    return showToast('请输入天数')
  }
  // 取城市名：选择器返回 "省 / 市"，热门城市直接是城市名
  const cityName = formData.city.includes(' / ')
    ? formData.city.split(' / ')[1]
    : formData.city
  router.push({
    path: '/detail',
    query: {
      city: cityName,
      budget: formData.budget,
      day: formData.day
    }
  })
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: var(--paper-bg);
  padding-bottom: 76px;
}

.hero {
  position: relative;
  height: 330px;
  overflow: hidden;
  color: #fffaf0;
  background: #786d58;
}

.hero-image,
.hero-shade {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.hero-image {
  object-fit: cover;
  object-position: center 48%;
}

/* 城市切换时顶部大图淡入淡出 */
.hero-fade-enter-active {
  transition: opacity 480ms ease;
}
.hero-fade-enter-from {
  opacity: 0;
}

.hero-shade {
  background:
    linear-gradient(90deg, rgba(28, 32, 29, 0.72) 0%, rgba(37, 38, 32, 0.38) 58%, rgba(36, 34, 28, 0.12) 100%),
    linear-gradient(0deg, rgba(30, 31, 27, 0.28), transparent 46%);
}

.hero-content {
  position: relative;
  z-index: 1;
  height: 100%;
  padding: max(22px, env(safe-area-inset-top)) 25px 32px;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 10px;
  letter-spacing: 2px;
  color: rgba(255, 250, 240, 0.88);
}

.brand-mark {
  display: grid;
  width: 27px;
  height: 27px;
  place-items: center;
  border: 1px solid rgba(255, 250, 240, 0.8);
  border-radius: 50%;
  font-family: var(--font-serif);
  font-size: 15px;
}

.hero-kicker {
  margin-top: 42px;
  font-size: 12px;
  letter-spacing: 3px;
  color: #f0dcae;
}

.hero h1 {
  margin: 7px 0 10px;
  color: #fffaf0;
  font-family: var(--font-serif);
  font-size: 38px;
  font-weight: 500;
  letter-spacing: 2px;
  line-height: 1.22;
}

.hero-caption {
  font-size: 12px;
  letter-spacing: 0.5px;
  color: rgba(255, 250, 240, 0.82);
}

.hero-stamp {
  position: absolute;
  right: 26px;
  bottom: 41px;
  display: grid;
  width: 52px;
  height: 52px;
  place-content: center;
  border: 1px solid rgba(244, 208, 163, 0.85);
  border-radius: 50%;
  color: #f4d0a3;
  font-family: var(--font-serif);
  font-size: 11px;
  line-height: 1.3;
  text-align: center;
  transform: rotate(12deg);
}

.hero-edge {
  position: absolute;
  right: -5%;
  bottom: -19px;
  left: -5%;
  height: 33px;
  border-radius: 50% 50% 0 0 / 70% 70% 0 0;
  background: var(--paper-bg);
}

.page-content {
  position: relative;
  z-index: 2;
  padding: 0 17px 20px;
  margin-top: -2px;
}

.planner {
  padding: 20px 18px 14px;
  margin-bottom: 30px;
  border: 1px solid rgba(216, 205, 186, 0.75);
  border-radius: 19px;
  background: var(--paper-white);
  box-shadow: 0 8px 24px rgba(74, 59, 39, 0.09);
}

.planner-heading,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.eyebrow {
  color: #a08d70;
  font-size: 9px;
  letter-spacing: 1.8px;
}

.planner h2,
.section-heading h2 {
  margin: 4px 0 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 600;
}

.planner-icon {
  display: grid;
  width: 37px;
  height: 37px;
  place-items: center;
  border-radius: 12px;
  background: #f7eee1;
  color: var(--stamp-red);
  font-size: 19px;
}

.destination-picker {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 13px 0 12px;
  margin-top: 14px;
  border: 0;
  border-bottom: 1px solid #eee6da;
  background: transparent;
  color: var(--ink);
  text-align: left;
}

.destination-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border-radius: 12px;
  background: #f6eee3;
  color: var(--stamp-red);
  font-size: 19px;
}

.destination-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.field-label {
  color: #938875;
  font-size: 11px;
}

.destination-value {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.destination-value.placeholder {
  color: #aaa18f;
  font-weight: 400;
}

.destination-arrow,
.shortcut-arrow {
  color: #b9ad99;
  font-size: 13px;
}

.quick-picks {
  display: flex;
  align-items: center;
  gap: 7px;
  overflow-x: auto;
  padding: 11px 0 2px;
  scrollbar-width: none;
}

.quick-picks::-webkit-scrollbar {
  display: none;
}

.quick-pick,
.more-cities {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 11px;
  border: 1px solid #e9dfcf;
  border-radius: 16px;
  background: #fffdf8;
  color: #756b5b;
  font-size: 11px;
}

.quick-pick.selected {
  border-color: var(--lake-teal);
  background: #edf5f1;
  color: #286a62;
}

.more-cities {
  gap: 3px;
  padding-right: 4px;
  border: 0;
  background: transparent;
  color: var(--stamp-red);
}

.more-cities .van-icon {
  font-size: 10px;
}

.trip-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 11px;
  margin-top: 13px;
}

.trip-field {
  min-width: 0;
  padding: 10px 11px 8px;
  border: 1px solid #eee6da;
  border-radius: 11px;
  background: #fffefa;
}

.input-line {
  display: flex;
  height: 25px;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
}

.input-line input {
  width: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font: 600 15px var(--font-sans);
}

.input-line input::placeholder {
  color: #b9b1a4;
  font-size: 12px;
  font-weight: 400;
}

.input-line input::-webkit-inner-spin-button,
.input-line input::-webkit-outer-spin-button {
  margin: 0;
  appearance: none;
}

.input-prefix {
  color: var(--stamp-red);
  font-size: 15px;
  font-weight: 600;
}

.input-suffix {
  flex: none;
  color: #9a907f;
  font-size: 11px;
}

.submit-btn {
  height: 46px;
  margin-top: 15px;
  border: 0;
  background: linear-gradient(105deg, #c4473a, #ad392f);
  box-shadow: 0 5px 12px rgba(196, 71, 58, 0.2);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
}

.submit-btn :deep(.van-button__content) {
  gap: 7px;
}

.planner-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 10px;
  color: #9b907d;
  font-size: 10px;
}

.planner-note .van-icon {
  color: var(--lake-teal);
}

.destinations-section {
  margin-bottom: 27px;
}

.section-heading {
  align-items: flex-end;
  margin-bottom: 13px;
}

.section-heading h2 {
  font-size: 20px;
}

.section-note {
  padding-bottom: 3px;
  color: #9a8d78;
  font-family: var(--font-serif);
  font-size: 11px;
}

.destination-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 11px;
}

.destination-card {
  position: relative;
  height: 130px;
  overflow: hidden;
  border: 1px solid rgba(216, 205, 186, 0.8);
  border-radius: 13px;
  background: #a59a82;
  box-shadow: 0 3px 10px rgba(74, 59, 39, 0.09);
}

.destination-select {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  overflow: hidden;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.destination-select:active {
  transform: scale(0.98);
}

.destination-select img,
.destination-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.destination-select img {
  object-fit: cover;
  transition: transform 350ms ease;
}

.destination-select:active img {
  transform: scale(1.04);
}

.destination-overlay {
  background: linear-gradient(0deg, rgba(29, 32, 28, 0.78), rgba(34, 34, 30, 0.02) 78%);
}

.destination-card-copy {
  position: absolute;
  right: 12px;
  bottom: 11px;
  left: 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  color: #fffaf0;
}

.destination-name {
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
}

.destination-description {
  overflow: hidden;
  color: rgba(255, 250, 240, 0.85);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-mark {
  position: absolute;
  top: 9px;
  right: 9px;
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border-radius: 50%;
  background: var(--lake-teal);
  color: white;
  font-size: 12px;
}

.shortcuts {
  padding: 2px 15px;
  border-top: 1px solid rgba(216, 205, 186, 0.8);
}

.shortcut-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border: 0;
  border-bottom: 1px solid rgba(216, 205, 186, 0.65);
  background: transparent;
  color: var(--ink);
  text-align: left;
}

.shortcut-item:last-child {
  border-bottom: 0;
}

.shortcut-icon {
  display: grid;
  width: 39px;
  height: 39px;
  flex: none;
  place-items: center;
  border-radius: 13px;
  font-size: 19px;
}

.chat-icon {
  background: #e8f0eb;
  color: var(--lake-teal);
}

.profile-icon {
  background: #f7eae2;
  color: var(--stamp-red);
}

.shortcut-item > span:nth-child(2) {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.shortcut-item strong {
  font-size: 13px;
  font-weight: 600;
}

.shortcut-item small {
  color: #948a79;
  font-size: 10px;
}

@media (min-width: 600px) {
  .hero {
    height: 370px;
  }

  .hero-content {
    padding-right: 32px;
    padding-left: 32px;
  }

  .page-content {
    padding-right: 24px;
    padding-left: 24px;
  }

  .destination-card {
    height: 150px;
  }
}

@media (max-width: 360px) {
  .page-content {
    padding-right: 12px;
    padding-left: 12px;
  }

  .planner {
    padding-right: 14px;
    padding-left: 14px;
  }

  .destination-card {
    height: 115px;
  }
}
</style>
