import { fetchStream } from '../utils/request'

/**
 * 流式对话接口（SSE / NDJSON）
 * 后端返回 event-stream，每一行为一个 JSON：{"type":"chunk","content":"..."}
 *
 * @param {Object}   params
 * @param {string}   params.message   用户输入的问题
 * @param {Function} [params.onChunk] 每收到一个 content 片段时回调
 * @param {Function} [params.onDone]  流结束时回调
 * @param {Function} [params.onError] 出错时回调
 */
export const chatStream = ({ message, onChunk, onDone, onError }) => {
  return fetchStream('travel/chat', { message }, onChunk, onDone, onError)
}
