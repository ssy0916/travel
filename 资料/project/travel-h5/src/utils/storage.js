/**
 * 个人中心相关数据的统一存储层
 * - 行程（tripStore）：P2 起走后端接口（H2 持久化，按 X-Device-Id 匿名设备隔离）
 * - 其余 store：仍基于 localStorage，后端接口就绪后只需把各 store 内的读写替换为接口请求
 */

import { listTrips, getTrip, createTrip, updateTrip, deleteTrip } from '../api/trips.js'

const PREFIX = 'travel_'

function read(key, fallback) {
  try {
    const raw = localStorage.getItem(PREFIX + key)
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

function write(key, value) {
  localStorage.setItem(PREFIX + key, JSON.stringify(value))
}

function remove(key) {
  localStorage.removeItem(PREFIX + key)
}

function genId(prefix) {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
}

/** 格式化为 YYYY-MM-DD HH:mm */
export function formatTime(ts) {
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/* ---------------- 我的行程（后端持久化） ---------------- */
const TRIPS = 'trips' // 旧 localStorage 数据 key，仅用于一次性迁移
const TRIPS_MIGRATED = 'trips_migrated'

export const tripStore = {
  /**
   * 分页查询行程
   * @param {Object} params { page=0, size=10, city? }
   * @returns {Promise<{list:Array,total:number,page:number,size:number,hasMore:boolean}>}
   */
  async list(params = {}) {
    const { data } = await listTrips(params)
    return data || { list: [], total: 0, page: 0, size: 10, hasMore: false }
  },
  /**
   * 行程详情（含完整行程 JSON）
   * @returns {Promise<Object|null>}
   */
  async get(id) {
    const { data } = await getTrip(id)
    return data || null
  },
  /**
   * 保存行程；内容完全相同时后端返回已存在记录且 duplicated=true
   * @returns {Promise<Object>}
   */
  async add(itinerary, note) {
    const { data } = await createTrip({ itinerary, note })
    return data
  },
  /** 编辑备注写回 */
  async updateNote(id, note) {
    const { data } = await updateTrip(id, { note })
    return data
  },
  /** 编辑整体行程写回 */
  async updateItinerary(id, itinerary) {
    const { data } = await updateTrip(id, { itinerary })
    return data
  },
  async remove(id) {
    const { data } = await deleteTrip(id)
    return data
  },
  /**
   * 旧 localStorage 行程一次性迁移到后端；迁移成功后写入标记不再重复执行
   * @returns {Promise<number>} 实际迁移条数
   */
  async migrate() {
    if (read(TRIPS_MIGRATED, false)) return 0
    const legacy = read(TRIPS, [])
    if (!legacy.length) {
      write(TRIPS_MIGRATED, true)
      return 0
    }
    let migrated = 0
    for (const t of legacy) {
      try {
        await createTrip({ itinerary: t.data, note: '' })
        migrated++
      } catch (e) {
        console.warn('迁移旧行程失败：', t.id, e.message)
      }
    }
    if (migrated === legacy.length) {
      remove(TRIPS)
      write(TRIPS_MIGRATED, true)
    }
    return migrated
  }
}

/* ---------------- 景点收藏 ---------------- */
const FAVORITES = 'favorites'
export const favoriteStore = {
  list() {
    return read(FAVORITES, []).sort((a, b) => b.createdAt - a.createdAt)
  },
  has(city, name) {
    return read(FAVORITES, []).some((f) => f.city === city && f.name === name)
  },
  add(item) {
    const favs = read(FAVORITES, [])
    if (favs.some((f) => f.city === item.city && f.name === item.name)) return
    favs.push({ id: genId('fav'), createdAt: Date.now(), ...item })
    write(FAVORITES, favs)
  },
  remove(id) {
    write(FAVORITES, read(FAVORITES, []).filter((f) => f.id !== id))
  },
  removeByCityName(city, name) {
    write(FAVORITES, read(FAVORITES, []).filter((f) => !(f.city === city && f.name === name)))
  }
}

/* ---------------- 浏览历史（按城市去重，仅保留最近一次） ---------------- */
const HISTORY = 'history'
export const historyStore = {
  list() {
    return read(HISTORY, []).sort((a, b) => b.viewedAt - a.viewedAt)
  },
  add(record) {
    const list = read(HISTORY, []).filter((h) => h.city !== record.city)
    list.push({ id: genId('his'), viewedAt: Date.now(), ...record })
    write(HISTORY, list)
  },
  clear() {
    remove(HISTORY)
  }
}

/* ---------------- 常用出行人 ---------------- */
const CONTACTS = 'contacts'
export const contactStore = {
  list() {
    return read(CONTACTS, []).sort((a, b) => Number(b.isDefault) - Number(a.isDefault))
  },
  add(data) {
    const list = read(CONTACTS, [])
    const contact = { id: genId('con'), isDefault: false, ...data }
    // 首个联系人自动设为默认；设为默认时取消其他默认
    if (contact.isDefault || list.length === 0) {
      contact.isDefault = true
      list.forEach((c) => (c.isDefault = false))
    }
    list.push(contact)
    write(CONTACTS, list)
  },
  update(id, data) {
    const list = read(CONTACTS, [])
    if (data.isDefault) list.forEach((c) => (c.isDefault = false))
    const idx = list.findIndex((c) => c.id === id)
    if (idx > -1) list[idx] = { ...list[idx], ...data }
    write(CONTACTS, list)
  },
  remove(id) {
    const list = read(CONTACTS, []).filter((c) => c.id !== id)
    // 删除的是默认联系人时，把剩余第一个设为默认
    if (list.length > 0 && !list.some((c) => c.isDefault)) list[0].isDefault = true
    write(CONTACTS, list)
  }
}

/* ---------------- 消息通知 ---------------- */
const NOTICES = 'notices'
const defaultNotices = [
  {
    id: 'n_welcome',
    type: 'system',
    title: '欢迎使用智能旅游助手',
    content: '在这里你可以通过 AI 一键生成专属行程，收藏心仪景点，规划每一次完美出行。',
    time: Date.now() - 1000 * 60 * 60 * 24 * 2,
    read: false
  },
  {
    id: 'n_trip',
    type: 'trip',
    title: '国庆热门目的地预订提醒',
    content: '国庆假期临近，北京、西安、成都等热门城市门票紧张，建议提前 7 天以上预约，合理安排行程。',
    time: Date.now() - 1000 * 60 * 60 * 5,
    read: false
  },
  {
    id: 'n_promo',
    type: 'promo',
    title: '秋日限定线路上新',
    content: '喀纳斯、稻城亚丁、额济纳旗秋季线路已上线，在首页输入目的地即可获取 AI 定制行程。',
    time: Date.now() - 1000 * 60 * 30,
    read: false
  }
]
export const noticeStore = {
  list() {
    const list = read(NOTICES, null)
    if (!list) {
      write(NOTICES, defaultNotices)
      return [...defaultNotices].sort((a, b) => b.time - a.time)
    }
    return list.sort((a, b) => b.time - a.time)
  },
  unreadCount() {
    return this.list().filter((n) => !n.read).length
  },
  markRead(id) {
    const list = read(NOTICES, [])
    const item = list.find((n) => n.id === id)
    if (item) item.read = true
    write(NOTICES, list)
  },
  markAllRead() {
    const list = read(NOTICES, []).map((n) => ({ ...n, read: true }))
    write(NOTICES, list)
  }
}

/* ---------------- 设置 ---------------- */
const SETTINGS = 'settings'
const DEFAULT_SETTINGS = { noticeEnabled: true }
export const settingsStore = {
  get() {
    return { ...DEFAULT_SETTINGS, ...read(SETTINGS, {}) }
  },
  set(key, value) {
    const settings = this.get()
    settings[key] = value
    write(SETTINGS, settings)
  }
}

/* ---------------- 用户资料（登录态） ---------------- */
const USER = 'user'
export const userStore = {
  get() {
    return read(USER, null)
  },
  set(user) {
    write(USER, user)
  },
  clear() {
    remove(USER)
  }
}
