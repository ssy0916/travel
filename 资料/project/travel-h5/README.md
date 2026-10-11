# 旅游规划 H5

这是一个基于 Vue 3、Vite 和 Vant 的移动端旅游规划应用。前端通过 `/api` 调用同级目录以外的 Spring Boot 服务；开发时服务默认运行在 `3200` 端口。

## 运行要求

- Node.js：建议使用 22.12 或更新的 22.x 版本（也可使用 20.19 或更新的 20.x 版本）。
- npm：随 Node.js 安装。
- Java：运行后端需要 JDK 25。仓库内提供 Maven Wrapper，不必另外安装 Maven。

## 本地启动

需要分别启动后端和前端。下面的命令以 Windows PowerShell 为例。

### 1. 启动后端

在一个终端窗口执行：

```powershell
cd D:\旅游业务\travel-java
$env:LLM_API_KEY = "替换为你的大模型 API Key"
$env:AMAP_API_KEY = "替换为你的高德 Web 服务 Key"
.\mvnw.cmd spring-boot:run
```

注意 PowerShell 中必须写成 `.\mvnw.cmd`（带 `.\` 前缀），只输入 `mvnw.cmd` 会提示“无法将 mvnw.cmd 项识别为 cmdlet”。这是 PowerShell 的安全机制，它默认不搜索当前目录。

如果你使用的是命令提示符（CMD，提示符通常显示为 `D:\...>`），请改用 CMD 语法，并在同一个窗口启动后端：

```bat
cd /d D:\旅游业务\travel-java
set "LLM_API_KEY=替换为你的大模型 API Key"
set "AMAP_API_KEY=替换为你的高德 Web 服务 Key"
mvnw.cmd spring-boot:run
```

PowerShell 的 `$env:名称 = "值"` 语法不能直接在 CMD 中执行；CMD 设置的环境变量只在当前窗口有效。若 Key 中包含 `%` 等 CMD 特殊字符，建议改用 PowerShell 或 IDE 的环境变量配置，避免值被命令行解释。

如果执行 `.\mvnw.cmd` 后提示 `'powershell' 不是内部或外部命令` 和 `Cannot start maven from wrapper`，说明当前终端的 PATH 缺少 Windows PowerShell。Maven Wrapper 脚本内部依赖它。先在当前窗口执行下面这行，再重新启动后端：

```powershell
$env:Path += ";C:\Windows\System32\WindowsPowerShell\v1.0"
.\mvnw.cmd spring-boot:run
```

该行只对当前窗口生效；要永久修复，请把 `C:\Windows\System32\WindowsPowerShell\v1.0` 加入系统环境变量 Path。

大模型 Key 和高德 Key 都是可选的。没有配置时，服务仍可启动，但行程推荐会使用降级内容，聊天功能也无法正常调用大模型。Key 只应放在本机环境变量或部署平台的密钥管理功能中，不能提交到仓库或写进前端代码。

后端启动后，可访问 `http://localhost:3200/api/travel/hello` 检查服务是否正常。

### 2. 启动前端

在另一个终端窗口执行：

```powershell
cd ...\旅游业务\资料\project\travel-h5
npm ci
npm run dev
```

打开终端显示的本地地址，通常是 `http://localhost:5173/`。Vite 会把前端的 `/api` 请求转发到本机 `http://localhost:3200`。

如果希望用手机在同一 Wi-Fi 下调试，继续使用 `npm run dev`，并访问终端输出的 `Network` 地址。手机和电脑需要连接同一局域网；Windows 防火墙也可能需要允许 Vite 使用的端口。电脑更换网络后，局域网地址可能变化。`npm run dev:local` 只允许本机访问。

## Docker 启动后端

项目目前只为 Spring Boot 后端提供 Dockerfile；前端没有独立镜像，也没有 Docker Compose 配置。容器化后端后，前端仍需在本机开发服务器运行，或按下文构建后部署到静态网站服务。

安装并启动 Docker Desktop 后，在 PowerShell 中执行：

```powershell
cd ...\旅游业务\travel-java
docker build -t travel-java:local .
docker run -d --name travel-java `
  -p 3200:3200 `
  -e LLM_API_KEY="替换为你的大模型 API Key" `
  -e AMAP_API_KEY="替换为你的高德 Web 服务 Key" `
  -v travel-data:/app/data `
  -v travel-logs:/app/logs `
  travel-java:local
```

如果暂时不使用大模型或高德服务，可以省略对应的 `-e` 参数；后端仍可启动，但行程推荐会降级，聊天无法正常调用大模型。不要把真实 Key 写入 Dockerfile、前端代码或提交到仓库。用于公开部署时，应使用部署平台的密钥管理功能，而不是把 Key 留在命令历史中。

确认容器和服务状态：

```powershell
docker ps
docker logs -f travel-java
Invoke-WebRequest http://localhost:3200/api/travel/hello
```

停止后端：

```powershell
docker stop travel-java
```

`travel-data` 和 `travel-logs` 是 Docker 命名卷，容器重建后仍保留数据。更新镜像时，先停止并移除旧容器，再重新构建和运行；不要删除这两个卷，否则行程数据或日志会丢失。

## 构建与部署前端

生成生产文件：

```powershell
npm ci
npm run build
```

构建结果位于 `dist`。将该目录中的静态文件部署到 Nginx、对象存储静态网站或其他静态托管服务即可。部署时不要上传 `node_modules`；`dist` 是构建产物，更新代码后应重新构建。

正式环境还需要满足以下条件：

1. 运行 `travel-java` 后端，并在部署平台配置 `LLM_API_KEY`、`AMAP_API_KEY` 等所需环境变量。不要把密钥打包进前端。
2. 在静态网站所在域名配置 `/api/` 反向代理到后端的 `3200` 端口。前端默认请求当前域名下的 `/api`；Vite 的代理配置只在开发服务器中生效，`npm run preview` 也不是生产 API 代理。
3. 流式聊天使用 SSE。代理需要支持长连接，关闭响应缓冲，并允许足够长的读取超时，否则聊天内容可能延迟显示或中途断开。
4. 本应用使用 Vue Router 的 History 模式。静态服务器需要将未匹配到文件的页面路由回退到 `index.html`，否则直接刷新 `/trips` 等页面可能返回 404。
5. 后端行程数据存放在 H2 文件数据库中。部署容器时，为 `/app/data` 配置持久化存储；使用临时磁盘或删除数据卷可能导致数据丢失。后端 Dockerfile 位于 [travel-java/Dockerfile](../../../travel-java/Dockerfile)。
6. `/actuator/prometheus` 会提供运行指标，生产环境应限制其访问范围。

## 使用前请了解

- 当前没有账号登录系统。行程接口使用浏览器保存的 `X-Device-Id` 做简单隔离；这不是身份认证，设备标识可被伪造。公开互联网部署时，不要存放真实个人信息或敏感资料。若面向公众长期开放，应先补上可靠的用户认证、授权和接口限流。
- 清除浏览器数据会同时清除本地收藏等数据，也会生成新的设备标识；原设备标识下的行程不会自动出现在新标识下。
- 行程可能是降级模板或示例数据。景点、交通、门票、开放时间、天气和住宿信息请出行前向相关官方渠道核实；酒店搜索结果不代表价格或有房保证。
- 其他说明见 [接口文档](docs/itinerary-api.md)。

## 常用命令

| 命令 | 用途 |
|---|---|
| `npm run dev` | 启动开发服务器，并开放局域网访问 |
| `npm run dev:local` | 启动仅本机可访问的开发服务器 |
| `npm run build` | 构建生产静态文件到 `dist` |
| `npm run preview` | 在本机预览构建结果，不适合作为正式生产服务器 |
