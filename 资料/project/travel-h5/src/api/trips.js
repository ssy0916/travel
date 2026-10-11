import { get, post, put, del } from '../utils/request'

/**
 * 行程持久化接口（/api/travel/trips）
 * 通过 X-Device-Id 请求头做匿名设备隔离；设备号惰性生成并持久化在 localStorage。
 */

const DEVICE_KEY = 'travel_device_id'

/** 读取（不存在则生成）匿名设备标识 */
function getDeviceId() {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    id = `dev_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
    localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}

/** 所有行程请求统一携带设备头 */
function withDevice(config = {}) {
  return {
    ...config,
    headers: {
      ...(config.headers || {}),
      'X-Device-Id': getDeviceId()
    }
  }
}

/**
 * 分页查询行程
 * @param {Object} params { page=0, size=10, city? }
 */
export const listTrips = (params = {}) => {
  return get('/travel/trips', params, withDevice({ showLoading: false }))
}

/** 行程详情（含完整行程 JSON） */
export const getTrip = (id) => {
  return get(`/travel/trips/${id}`, {}, withDevice())
}

/** 保存行程（内容完全相同时后端返回已存在记录，duplicated=true） */
export const createTrip = ({ itinerary, note } = {}) => {
  return post('/travel/trips', { itinerary, note }, withDevice())
}

/** 编辑写回：更新备注；如传 itinerary 则整体替换行程内容 */
export const updateTrip = (id, payload = {}) => {
  return put(`/travel/trips/${id}`, payload, withDevice())
}

/** 删除行程 */
export const deleteTrip = (id) => {
  return del(`/travel/trips/${id}`, {}, withDevice())
}
