/**
 * 城市名 → 城市风光图 的确定性映射。
 * 图片均为按城市地标专门生成的本地素材（src/assets/cities/），
 * 未收录城市使用通用旅行风光兜底，绝不使用随机外链，避免图文错配。
 */
import beijing from '../assets/cities/beijing.jpg'
import shanghai from '../assets/cities/shanghai.jpg'
import guangzhou from '../assets/cities/guangzhou.jpg'
import shenzhen from '../assets/cities/shenzhen.jpg'
import hangzhou from '../assets/cities/hangzhou.jpg'
import chengdu from '../assets/cities/chengdu.jpg'
import xian from '../assets/cities/xian.jpg'
import nanjing from '../assets/cities/nanjing.jpg'
import chongqing from '../assets/cities/chongqing.jpg'
import xiamen from '../assets/cities/xiamen.jpg'
import suzhou from '../assets/cities/suzhou.jpg'
import sanya from '../assets/cities/sanya.jpg'
import lijiang from '../assets/cities/lijiang.jpg'
import changsha from '../assets/cities/changsha.jpg'
import wuhan from '../assets/cities/wuhan.jpg'
import qingdao from '../assets/cities/qingdao.jpg'
import defaultCity from '../assets/cities/default.jpg'

/** 城市名 → 图片；键与 CityKnowledgeBase 的收录城市保持一致 */
const CITY_IMAGE_MAP = {
  北京: beijing,
  上海: shanghai,
  广州: guangzhou,
  深圳: shenzhen,
  杭州: hangzhou,
  成都: chengdu,
  西安: xian,
  南京: nanjing,
  重庆: chongqing,
  厦门: xiamen,
  苏州: suzhou,
  三亚: sanya,
  丽江: lijiang,
  长沙: changsha,
  武汉: wuhan,
  青岛: qingdao
}

/** 去掉“市”后缀、去除空白，提升匹配命中率 */
function normalize(city) {
  if (!city) return ''
  const c = String(city).trim()
  return c.endsWith('市') && c.length > 2 ? c.slice(0, -1) : c
}

/**
 * 获取城市风光图 URL。
 * @param {string} city 城市名（支持“杭州市”这类带后缀写法）
 * @returns {string} 本地图片资源 URL，未收录时返回通用默认图
 */
export function cityImage(city) {
  const key = normalize(city)
  return CITY_IMAGE_MAP[key] || defaultCity
}

/** 该城市是否有专属配图（可用于决定是否展示“城市风光”角标） */
export function hasCityImage(city) {
  return Boolean(CITY_IMAGE_MAP[normalize(city)])
}
