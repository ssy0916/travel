<template>
  <div class="page">
    <van-nav-bar title="帮助与客服" left-arrow @click-left="onBack" safe-area-inset-top />

    <div class="content">
      <div class="card">
        <div class="section-title">常见问题</div>
        <van-collapse v-model="activeFaq">
          <van-collapse-item v-for="(faq, i) in faqs" :key="i" :title="faq.q" :name="i">
            <div class="faq-a">{{ faq.a }}</div>
          </van-collapse-item>
        </van-collapse>
      </div>

      <div class="card contact-card">
        <div class="section-title">联系我们</div>
        <van-cell title="在线客服" icon="chat-o" is-link @click="goChat">
          <template #label>
            <span class="cell-label">AI 助手 7×24 小时为你解答</span>
          </template>
        </van-cell>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'

const router = useRouter()
const activeFaq = ref([0]) // 默认展开第 1 条
const onBack = () => router.back()
const goChat = () => router.push('/chat')
const callHotline = () => {
  window.location.href = 'tel:4008880000'
  showToast('正在呼叫客服热线')
}

const faqs = [
  {
    q: '如何生成一份行程？',
    a: '在首页选择目的地城市，填写预算和天数，点击「开始规划」，AI 会为你生成包含景点安排、交通指引、预算明细和出行提示的完整行程。'
  },
  {
    q: '如何收藏景点？',
    a: '在行程详情页，每个景点卡片右上角都有星标按钮，点击即可收藏；在「个人中心 - 我的收藏」中可以查看和取消收藏。'
  },
  {
    q: '生成的行程可以保存多久？',
    a: '行程、收藏等数据保存在本机浏览器中，不登录账号也能使用；清除浏览器缓存或更换设备会导致数据丢失，建议重要行程及时截图保存。'
  },
  {
    q: '行程中的门票价格准确吗？',
    a: '行程由 AI 大模型生成，价格和开放时间仅供参考。热门景点（如故宫、国博等）需提前预约，建议出行前通过官方渠道再次核实。'
  },
  {
    q: '如何添加常用出行人？',
    a: '进入「个人中心 - 出行人」，点击右上角 + 号填写姓名、手机号和身份证号即可，预订门票或跟团产品时可直接选择，无需重复填写。'
  }
]
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: var(--paper-bg);
}
.content {
  padding: 12px;
}
.card {
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: var(--shadow-paper);
  overflow: hidden;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--ink);
  font-family: var(--font-serif);
}
.faq-a {
  font-size: 13px;
  color: var(--ink);
  line-height: 1.7;
}
.contact-card {
  padding: 16px 0 0;
}
.contact-card .section-title {
  padding: 0 16px;
}
.cell-label {
  font-size: 12px;
  color: var(--van-text-color-3);
}
.hotline {
  font-size: 14px;
  color: var(--lake-teal);
}
</style>
