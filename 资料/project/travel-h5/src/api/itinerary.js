import { post } from '../utils/request'

/**
 * 生成行程（推荐）
 * 接口：POST /api/travel/recommend
 * @param {Object} params
 * @param {string} params.city   目的地城市，如 '北京'
 * @param {number} params.budget 预算（元），如 5000
 * @param {number} params.day    天数，如 2
 * @returns {Promise} 后端返回 { success, code, message, data }
 */
export const generateItinerary = (params) => {
  // 前端表单字段为 budget/day，后端 TravelRequestDTO 约定为 totalBudget/days
  return post('/travel/recommend', {
    city: params.city,
    days: Number(params.day),
    totalBudget: Number(params.budget)
  })
}
