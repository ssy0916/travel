<template>
  <div class="chat-page">
    <!-- 顶部导航 -->
    <van-nav-bar
      title="AI 旅游助手"
      left-arrow
      @click-left="onBack"
      safe-area-inset-top
    />

    <!-- 消息区 -->
    <div class="chat-content" ref="chatContent">
      <!-- 空状态 -->
      <template v-if="messages.length === 0">
        <div class="empty-state">
          <van-icon name="description" size="56" color="#c8c9cc" />
          <div class="empty-text">开始和 AI 助手对话吧！</div>
        </div>
        <div class="faq-section">
          <div class="faq-title">常见问题</div>
          <div class="faq-tags">
            <div
              v-for="q in faqList"
              :key="q"
              class="faq-tag"
              @click="sendMessage(q)"
            >
              {{ q }}
            </div>
          </div>
        </div>
      </template>

      <!-- 消息列表 -->
      <template v-else>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="msg-row"
          :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
        >
          <!-- AI 头像 -->
          <template v-if="msg.role === 'ai'">
            <div class="avatar ai-avatar">AI</div>
          </template>

          <div class="msg-body">
            <div
              class="msg-bubble"
              :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'"
            >
              <!-- AI 思考中且还没有内容 -->
              <span v-if="msg.role === 'ai' && msg.streaming && !msg.content" class="thinking">
                AI 正在思考中...
              </span>
              <span v-else class="msg-text">{{ msg.content }}</span>
              <!-- 流式光标 -->
              <span v-if="msg.role === 'ai' && msg.streaming && msg.content" class="cursor"></span>
            </div>
            <div class="msg-time">{{ msg.time }}</div>
          </div>

          <!-- 用户头像 -->
          <template v-if="msg.role === 'user'">
            <div class="avatar user-avatar">我</div>
          </template>
        </div>
      </template>
    </div>

    <!-- 底部输入栏 -->
    <div class="chat-input-bar">
      <van-field
        v-model="inputText"
        placeholder="输入您的问题..."
        class="chat-input"
        :disabled="isStreaming"
        @keyup.enter="onSend"
      />
      <van-button
        type="primary"
        round
        class="send-btn"
        :disabled="!inputText.trim() || isStreaming"
        @click="onSend"
      >
        发送
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { chatStream } from '../api/chat'

const router = useRouter()

const inputText = ref('')
const isStreaming = ref(false)
const chatContent = ref(null)

// 消息列表
const messages = ref([])
let msgId = 0

// 常见问题
const faqList = [
  '北京有哪些必去的景点？',
  '上海美食推荐',
  '成都三日游攻略',
  '如何选择旅行保险？'
]

// 常见问题预设答案（即时回复，无需调用接口）
const mockReply = (question) => {
  const replies = {
    '北京有哪些必去的景点？':
      '北京必去景点推荐：\n1. 故宫博物院（提前7天预约）\n2. 天安门广场\n3. 八达岭长城\n4. 颐和园\n5. 天坛公园\n6. 鸟巢水立方（夜景）\n建议安排2-3天游览，感受古都的历史与现代交融。',
    '上海美食推荐':
      '上海特色美食：\n• 小笼包（南翔馒头店）\n• 生煎包（大壶春）\n• 本帮红烧肉\n• 糖醋排骨\n• 蟹粉豆腐\n• 葱油拌面\n• 鲜肉月饼\n可去城隍庙、云南南路美食街品尝地道本帮味。',
    '成都三日游攻略':
      '成都三日游：\nDay1：宽窄巷子 → 人民公园 → 春熙路\nDay2：大熊猫繁育研究基地 → 锦里古街\nDay3：都江堰 → 青城山\n美食必尝：火锅、串串、担担面、龙抄手，体验慢生活的惬意。',
    '如何选择旅行保险？':
      '选择旅行保险建议：\n1. 看保障范围（医疗、意外、航班延误、行李丢失）\n2. 看保额（出境游医疗保额建议30万以上）\n3. 看免责条款\n4. 出境游优先选含紧急救援的产品\n根据目的地和行程天数灵活选择。'
  }
  return replies[question] || ''
}

// 格式化当前时间 HH:mm
const nowTime = () => {
  const d = new Date()
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContent.value) {
      chatContent.value.scrollTop = chatContent.value.scrollHeight
    }
  })
}

const sendMessage = (text) => {
  const content = text.trim()
  if (!content || isStreaming.value) return

  // 用户消息
  messages.value.push({
    id: ++msgId,
    role: 'user',
    content,
    time: nowTime()
  })
  inputText.value = ''
  scrollToBottom()

  // 常见问题：直接返回预设答案（即时回复，无需等待接口）
  const preset = mockReply(content)
  if (preset) {
    messages.value.push({
      id: ++msgId,
      role: 'ai',
      content: preset,
      time: nowTime(),
      streaming: false
    })
    scrollToBottom()
    return
  }

  // 自由输入：调用流式 AI 接口
  const aiMsg = {
    id: ++msgId,
    role: 'ai',
    content: '',
    time: nowTime(),
    streaming: true
  }
  messages.value.push(aiMsg)
  isStreaming.value = true
  scrollToBottom()

  chatStream({
    message: content,
    onChunk: (chunk) => {
      aiMsg.content += chunk
      scrollToBottom()
    },
    onDone: () => {
      aiMsg.streaming = false
      aiMsg.time = nowTime()
      isStreaming.value = false
    },
    onError: (e) => {
      aiMsg.content = '抱歉，出了点小问题，请稍后再试～'
      aiMsg.streaming = false
      isStreaming.value = false
      console.error('chat stream error:', e)
    }
  })
}

const onSend = () => {
  sendMessage(inputText.value)
}

const onBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f7f8fa;
}

/* 消息区 */
.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  padding-bottom: 70px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 0 30px;
}
.empty-text {
  margin-top: 16px;
  font-size: 15px;
  color: #969799;
}

/* 常见问题 */
.faq-section {
  margin-top: 12px;
}
.faq-title {
  font-size: 14px;
  color: #969799;
  text-align: center;
  margin-bottom: 12px;
}
.faq-tags {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 0 8px;
}
.faq-tag {
  background: #fff;
  border: 1px solid #ebedf0;
  border-radius: 18px;
  padding: 10px 12px;
  font-size: 13px;
  color: #323233;
  text-align: center;
}

/* 消息行 */
.msg-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 8px;
}
.msg-user {
  flex-direction: row-reverse;
}
.msg-body {
  display: flex;
  flex-direction: column;
  max-width: 75%;
}
.msg-user .msg-body {
  align-items: flex-end;
}

/* 头像 */
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #fff;
  flex-shrink: 0;
}
.ai-avatar {
  background: linear-gradient(135deg, #4facfe, #00f2fe);
}
.user-avatar {
  background: #07c160;
}

/* 气泡 */
.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}
.bubble-ai {
  background: #fff;
  color: #323233;
  border-top-left-radius: 4px;
}
.bubble-user {
  background: #1989fa;
  color: #fff;
  border-top-right-radius: 4px;
}

/* 时间 */
.msg-time {
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 4px;
}

/* AI 思考中 */
.thinking {
  color: #969799;
  font-size: 13px;
}

/* 流式光标 */
.cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  background: #1989fa;
  vertical-align: middle;
  margin-left: 2px;
  animation: blink 1s steps(2) infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 底部输入栏 */
.chat-input-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 50px; /* 避开底部 tabbar */
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-top: 1px solid #ebedf0;
  z-index: 100;
}
.chat-input {
  flex: 1;
}
.send-btn {
  flex-shrink: 0;
}
</style>
