# P2 完成报告：行程持久化与历史编辑

> 阶段：P2（行程持久化与历史编辑）
> 状态：已完成并验收通过
> 前置：P0（安全与数据契约）、P1（高德数据源与节点级编排）

## 1. 修改文件清单

### 后端 `travel-java`（新增 7 个文件）

| 文件 | 说明 |
|---|---|
| `pom.xml` | 新增 `spring-boot-starter-data-jpa` + `h2` |
| `src/main/resources/application.yml` | H2 文件库 `./data/travel`，`ddl-auto: update` |
| `entity/Trip.java` | 行程实体（deviceId / payloadHash / note / payload / createdAt / updatedAt） |
| `repository/TripRepository.java` | JPA 分页、城市筛选、去重、设备隔离查询 |
| `dto/TripSaveRequest.java` | 保存/编辑请求 `{itinerary, note?}` |
| `vo/TripVO.java` + `vo/TripPageVO.java` | 详情/分页返回结构 |
| `service/TripService.java` + `controller/TripController.java` | 业务逻辑与 5 个接口 |

### 前端 `travel-h5`（改 5 个文件）

| 文件 | 说明 |
|---|---|
| `src/api/trips.js` | 新增，自动生成并携带 `X-Device-Id` 头 |
| `src/utils/storage.js` | tripStore 改异步接口实现 + `migrate()` 旧数据一次性迁移 |
| `src/views/Trips.vue` | 分页（van-list）+ 城市筛选 + 降级标识 + 备注显示 |
| `src/views/Detail.vue` | 已保存行程改接口读取、保存走后端去重、新增备注编辑 dialog |
| `src/views/Profile.vue` | 行程数量改异步读取 |

### 文档

- `travel-java/.gitignore` 加 `data/`
- `README.md` 与 `docs/itinerary-api.md` 补充 trips 接口说明（v1.3）

## 2. 接口清单

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/travel/trips?page=&size=&city=` | GET | 分页查询，城市筛选可选；列表不返回完整 payload |
| `/api/travel/trips/{id}` | GET | 详情，`data.data` 为完整行程 JSON |
| `/api/travel/trips` | POST | 保存；内容完全相同（payload SHA-256）时返回已存在记录且 `duplicated=true` |
| `/api/travel/trips/{id}` | PUT | 编辑写回：`{note}` 仅更新备注；`{itinerary}` 整体替换并同步元字段 |
| `/api/travel/trips/{id}` | DELETE | 删除；不存在或非本设备返回外壳 `code:404` |

所有接口按请求头 `X-Device-Id` 做匿名设备隔离，未携带时归入 `anonymous`。
**这不是真实账号体系**，清除浏览器数据后会视为新设备。

## 3. 验收结果

| 验收项 | 结果 |
|---|---|
| 后端 `mvn test` | 7/7 通过（含 Spring 上下文加载，JPA+H2 装配正常） |
| 创建行程 | id 正常生成，元字段（city/day/budget/summary/degraded）从 itinerary 自动提取 |
| 内容去重 | 重复保存返回 `duplicated:true` 和已存在 id |
| 分页查询 | total 正确，按 createdAt 倒序，列表项 `data` 为 null |
| 城市筛选 | `city=北京` 只返回北京行程 |
| 行程详情 | 完整行程 JSON 在 `data.data` |
| 编辑备注写回 | PUT 后 note 更新，updatedAt 变化 |
| 设备隔离 | 其他 deviceId 访问返回 404 |
| 删除 | 删除后列表 total 减一 |
| 前端 `npm run build` | 构建通过 |
| 旧数据兼容 | 旧 localStorage 行程首次进入「我的行程」自动迁移到后端 |

## 4. 过程中修复的两个问题

1. **H2 保留字**：`day` 是 H2 关键字导致 SQL 报错。实体改为 `@Column(name = "trip_days")`，Java 属性保持 `day` 对齐前端字段。
2. **Jackson 版本不兼容**：Spring Boot 4.1 的 HTTP 消息转换器使用 Jackson 3（`tools.jackson`），无法反序列化 Jackson 2 的 `JsonNode`。DTO/VO 中的 `itinerary`/`data` 改为 `Map<String, Object>`。

## 5. 风险与未完成项

- 编辑能力有限：UI 目前只支持「备注」编辑写回；接口已支持整体替换 itinerary，前端编辑 UI 待后续
- 匿名设备隔离非账号体系，不应用于多用户场景
- H2 文件库适合开发/单机；生产部署建议换 MySQL（只改 `application.yml` 数据源，代码不动）
- 降级行程仍可保存（P0 遗留），后续可加提示

## 6. 下一步

P3：轻量城市知识库（`resources/cities/*.md` 按城市注入 prompt，标注来源与更新时间）。
