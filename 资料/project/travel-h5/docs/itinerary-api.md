# 智能旅游助手 - 接口文档（以实际代码为准）

> 版本：v1.4（P3 城市知识库）
> 后端：Spring Boot，端口 **3200**
> 前端调用入口：`src/api/itinerary.js` / `src/api/chat.js` / `src/api/trips.js`
> 开发环境 `/api` 由 Vite 代理到 `http://localhost:3200`（见 `vite.config.js`）

> 历史文档曾写过 `POST /api/itinerary/generate`、`code === 0`、端口 8080，
> 均已过期：实际路径为 `/api/travel/recommend`，成功码为 `code: 200`。

---

## 0. 统一响应外壳

非 SSE 接口统一返回 `Result<T>`：

```json
{ "success": true, "code": 200, "message": "请求成功", "data": { } }
```

| 字段 | 说明 |
|---|---|
| success | 业务是否成功 |
| code | `200` 成功；`400` 参数校验失败；`500` 服务异常 |
| message | 中文提示 |
| data | 业务数据 |

> 前端 `src/utils/request.js` 拦截器约定 `code === 0 || code === 200` 为成功，否则 reject 并 toast。

---

## 1. 生成行程

### 基本信息

| 项 | 值 |
|---|---|
| 接口路径 | `POST /api/travel/recommend` |
| Content-Type | `application/json` |
| 鉴权 | 暂无 |

### 请求参数（TravelRequestDTO）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| city | string | 是 | 目的地城市，如 `北京` |
| days | number | 是 | 行程天数，如 `2`（注意后端字段是 `days`） |
| totalBudget | number | 是 | 预算总额（元），如 `5000` |

**请求示例**

```json
{ "city": "北京", "days": 2, "totalBudget": 5000 }
```

### data 字段说明（ItineraryVO）

| 字段 | 类型 | 说明 |
|---|---|---|
| success | boolean | 固定 true（HTTP 始终 200，降级也返回 true） |
| city | string | 城市（服务端回填） |
| day | number | 天数（单数，前端字段名） |
| budget | number | 预算总额（服务端回填） |
| summary | string | 概览文案 |
| days | array | 每日行程，元素为「日结构」 |
| budgetBreakdown | object | 预算明细，降级模板中分项可能为 `null` |
| tips | string[] | 温馨提示 |
| degraded | boolean | **是否降级行程**（模板/示例） |
| degradeReason | string | 降级的中文原因 |
| sourceFlags | object | LLM、地理编码、天气、POI、住宿的数据来源，状态详见下方 |
| centerLng / centerLat | number 或 `null` | 高德地理编码返回的城市中心坐标 |
| weather | array | 高德天气预报；未配置或请求失败时为空数组 |
| hotelRecommendations | array | 高德住宿 POI 搜索结果，不代表价格、空房或质量保证 |

#### days[] 日结构

| 字段 | 类型 | 说明 |
|---|---|---|
| day | number | 第几天，从 1 开始 |
| title | string | 如 `第1天` |
| slots | array | 时段列表（上午/下午/晚上） |

#### days[].slots[] 时段结构

| 字段 | 类型 | 说明 |
|---|---|---|
| period | string | `上午` / `下午` / `晚上` |
| attractions | array | 景点列表，可能为空数组 |

#### slots[].attractions[] 景点结构

| 字段 | 类型 | 正常 | 降级模板 |
|---|---|---|---|
| name | string | 真实景点名 | 占位名称（含“待重新生成”） |
| duration | string | 游玩时长 | `null` |
| cost | string | 费用/门票 | `null` |
| transport | string | 交通方式 | `null` |
| description | string | 景点介绍 | 通用占位说明 |
| lng / lat | number 或 `null` | 仅唯一匹配到高德 POI 时返回 | `null` |
| ticket / openTime / photo / address | string 或 `null` | 仅高德 POI/详情实际返回时填充 | `null` |
| poiVerification | string | `verified` 或 `unverified` | `unverified` |

`transport` 是行程规划建议，不代表高德路线规划结果。`poiVerification` 只有在城市 adcode 下唯一匹配景点名称后才为 `verified`；未匹配或同名歧义时保留模型结果、不补真实坐标。门票和开放时间仅在高德实际返回时展示，出行前仍应向景区官方核实。

#### sourceFlags

示例：`{ "llm": "real", "geocode": "real", "weather": "real", "poi": "fallback", "hotel": "real", "knowledge": "real" }`

- `real`：该数据节点已从对应服务成功取得响应；`poi: real` 不代表每个行程景点都已匹配，单个景点以 `poiVerification` 为准。
- `fallback`：Key 未配置、节点请求失败或没有可用结果。节点独立降级，不影响其他节点。
- `knowledge`：`real` 表示该城市知识库已注入 prompt（`resources/cities/*.md`，整包注入，不做向量检索）；`fallback` 表示城市未收录或加载失败。
- 前端网络异常时使用本地 mock，LLM 来源标识为 `mock`。

> 坐标、图片、门票和开放时间只来自高德匹配结果，不从模型输出中接受这些字段，避免模型编造真实 POI 信息。
> 知识库内容按城市整包注入 prompt，仅作规划参考；门票价格、开放时间等请以景区官方信息为准。

#### budgetBreakdown 结构

| 字段 | 类型 |
|---|---|
| accommodation / dining / transport / tickets / other / total | number 或 `null`（降级模板） |

### 数据来源三态（P0 核心行为）

| 场景 | HTTP | data.success | degraded | sourceFlags.llm |
|---|---|---|---|---|
| LLM 正常返回并解析成功 | 200 | true | false | `real` |
| 未配 `LLM_API_KEY` / LLM 调用失败（欠费等）/ 两次 JSON 解析失败 | 200 | true | **true** | `fallback` |
| 参数缺失/非法（校验失败） | 200，外壳 code=400 | — | — | — |
| 前端连不上后端 | — | 前端 catch 后使用 `src/mock/itinerary.js`，自带 `degraded:true`、`llm:"mock"` 标识 | | | |

### 响应示例（LLM 正常）

```json
{
  "success": true, "code": 200, "message": "请求成功",
  "data": {
    "success": true,
    "city": "北京", "day": 2, "budget": 5000,
    "summary": "北京 · 2天行程",
    "days": [
      { "day": 1, "title": "第1天", "slots": [
        { "period": "上午", "attractions": [
          { "name": "故宫博物院", "duration": "3-4小时", "cost": "门票信息以景区为准",
            "transport": "交通建议（非高德路线规划结果）", "description": "景点介绍……",
            "lng": 116.397, "lat": 39.918, "ticket": null, "openTime": null,
            "photo": null, "address": "高德返回的 POI 地址", "poiVerification": "verified" }
        ] }
      ] }
    ],
    "budgetBreakdown": {
      "accommodation": 1200, "dining": 800, "transport": 500,
      "tickets": 200, "other": 2300, "total": 5000
    },
    "tips": ["故宫需提前在官方小程序预约。"],
    "degraded": false,
    "centerLng": 116.4, "centerLat": 39.9,
    "weather": [
      { "date": "YYYY-MM-DD", "dayWeather": "天气", "nightWeather": "天气",
        "dayTemperature": "温度", "nightTemperature": "温度" }
    ],
    "hotelRecommendations": [],
    "sourceFlags": {
      "llm": "real", "geocode": "real", "weather": "real",
      "poi": "real", "hotel": "real"
    }
  }
}
```

### 响应示例（降级模板，未配 Key）

```json
{
  "success": true, "code": 200, "message": "请求成功",
  "data": {
    "success": true,
    "city": "北京", "day": 2, "budget": 5000,
    "summary": "北京 · 2天行程（降级模板）",
    "days": [
      { "day": 1, "title": "第1天", "slots": [
        { "period": "上午", "attractions": [
          { "name": "城市标志性户外景点（待重新生成）", "duration": null,
            "cost": null, "transport": null,
            "description": "建议安排 1 个该城市的核心户外景点……" }
        ] }
      ] }
    ],
    "budgetBreakdown": {
      "accommodation": null, "dining": null, "transport": null,
      "tickets": null, "other": null, "total": 5000
    },
    "tips": [
      "当前为降级行程模板：未配置大模型 API Key（环境变量 LLM_API_KEY）……"
    ],
    "degraded": true,
    "degradeReason": "未配置大模型 API Key（环境变量 LLM_API_KEY），当前为降级行程模板",
    "weather": [],
    "hotelRecommendations": [],
    "sourceFlags": {
      "llm": "fallback", "geocode": "fallback", "weather": "fallback",
      "poi": "fallback", "hotel": "fallback"
    }
  }
}
```

> 通用降级模板不编造具体景点、票价与开放时间。P1 中若高德景点候选可用，LLM 降级时会按候选 POI 做简单顺序安排；这不是路线规划，仍需核实交通与营业信息。若没有候选，则返回不含具体景点的通用规则模板。两种降级结果都必须显示降级提示，不得当作完整的 AI 规划。

---

## 2. 流式对话

| 项 | 值 |
|---|---|
| 接口路径 | `POST /api/travel/chat` |
| Content-Type | `application/json` |
| 响应类型 | `text/event-stream`（SSE，逐行 JSON） |

请求：`{ "message": "北京有什么好玩的？" }`

每行一个 JSON 事件：

```json
{"type":"chunk","content":"片段"}
{"type":"done"}
{"type":"error","error":"大模型账户余额不足……"}
```

未配置 `LLM_API_KEY` 时立即推送一条 `error` 事件并结束流，不抛异常。
前端封装见 `src/utils/request.js` 的 `fetchStream()`。

---

## 3. 健康检查

`GET /api/travel/hello` → `{ "success": true, "code": 200, "data": "你好，旅游服务运行正常" }`，不依赖大模型。

---

## 4. 大模型配置（后端环境变量）

通过环境变量注入，**不要写入源码或仓库**：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `LLM_API_KEY` | 空 | 为空时服务正常启动，行程返回降级模板、chat 推送 error |
| `LLM_BASE_URL` | `https://api.deepseek.com/v1` | OpenAI 兼容地址（DeepSeek/千问/Kimi 等） |
| `LLM_MODEL` | `deepseek-chat` | 模型名 |
| `AMAP_API_KEY` | 空 | 高德 Web 服务 Key，仅后端使用；为空时跳过高德节点 |
| `AMAP_BASE_URL` | `https://restapi.amap.com` | 高德 Web 服务 API 地址，可用于本地模拟服务测试 |

IDEA：Run Configuration → Environment variables 配置 `LLM_API_KEY` 和 `AMAP_API_KEY`。
不同模型的流式与 JSON 输出（`response_format`）行为可能有差异，需要时单独适配。

高德 HTTP 调用使用超时、每秒最多 3 次的限流和熔断保护。该阶段使用高德 Web 服务地理编码、天气、景点/住宿 POI 和 POI 详情；高德没有返回的字段会保持为空，不以模型猜测补齐。

---

## 5. 前端降级与空状态约定

- `data.success === false`：显示错误空状态 + 「重新生成」按钮。
- `data.degraded === true`：正常渲染结构，顶部显示黄色降级提示条（文案取 `degradeReason`）。
- 景点 `duration/cost/transport/description` 缺失：对应行不渲染，不出现 `¥null`。
- 预算分项为 `null`：显示「待生成」。
- 网络异常：使用本地 mock（北京示例），并明确标识「本地示例数据」。

---

## 6. 行程持久化（P2 新增）

所有行程接口按请求头 `X-Device-Id` 做匿名设备隔离（前端自动生成并存在 localStorage）；
未携带时归入 `anonymous`。这不是真实账号体系，清除浏览器数据后会视为新设备。

数据存储：后端 H2 文件数据库（`travel-java/data/travel`），重启不丢。
前端旧 localStorage 行程在首次进入「我的行程」时自动一次性迁移到后端（`tripStore.migrate()`）。

### 6.1 分页查询

`GET /api/travel/trips?page=0&size=10&city=北京`（city 可选）

```json
{
  "success": true, "code": 200,
  "data": {
    "list": [
      { "id": 2, "city": "上海", "day": 3, "budget": 8000, "summary": "上海 · 3天行程",
        "degraded": true, "note": "周末游", "data": null, "duplicated": false,
        "createdAt": 1791051924751, "updatedAt": 1791051924751 }
    ],
    "total": 2, "page": 0, "size": 10, "hasMore": false
  }
}
```

> 列表项 `data` 为 null，完整行程走详情接口。`createdAt/updatedAt` 为 epoch 毫秒。

### 6.2 行程详情

`GET /api/travel/trips/{id}` — `data.data` 为完整行程 JSON（ItineraryVO 结构）。
记录不存在或不属于本设备：外壳 `code: 404`。

### 6.3 保存行程

`POST /api/travel/trips`

```json
{ "itinerary": { "city": "北京", "day": 2, "...": "完整行程 JSON" }, "note": "可选备注" }
```

内容完全相同（按 payload SHA-256）的行程不重复保存：返回已存在记录且 `duplicated: true`。

### 6.4 编辑写回

`PUT /api/travel/trips/{id}`

- `{ "note": "新备注" }`：仅更新备注
- `{ "itinerary": {...} }`：整体替换行程内容并同步元字段

记录不存在或不属于本设备：外壳 `code: 404`。

### 6.5 删除行程

`DELETE /api/travel/trips/{id}` → `data: true`；不存在时外壳 `code: 404`。
