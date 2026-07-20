# FatCatKill

FatCatKill 是由 Spring Boot API、Socket.IO Gateway、Vue 前端與既有 PostgreSQL 組成的多人遊戲。

## 服務

- `fatcatkill-api`: 遊戲規則、房間狀態與 PostgreSQL 持久化
- `fatcatkill-gateway`: WebSocket、房間廣播、玩家身分綁定與 API 白名單
- `fatcatkill-web`: 玩家前端；可用環境變數切換 Debug 工具
- `fatcatkill-web-debug`: 舊版 Debug 前端，保留但不再由環境建置啟動

## 環境設定

先建立 `.env`：

```bash
cp .env.example .env
```

必要設定：

```env
POSTGRES_USERNAME=postgres
POSTGRES_PASSWORD=your_password
SPRING_DATASOURCE_DOCKER_URL=jdbc:postgresql://host.docker.internal:5432/fatcatkill
BACKEND_CONTAINER_PORT=8080
BACKEND_BIND_ADDRESS=127.0.0.1
GATEWAY_PORT=8091
GATEWAY_CONTAINER_PORT=3000
FRONTEND_PORT=5173
VITE_GATEWAY_URL=
VITE_ENABLE_DEBUG_MODE=false
SPRING_PROFILES_ACTIVE=default
SPRING_JPA_HIBERNATE_DDL_AUTO=update
ENABLE_DEBUG_ACTIONS=false
LOG_LEVEL=info
```

`host.docker.internal` 代表同一台 NAS 主機。如果 PostgreSQL 位於其他主機，請改成該主機 IP 或 DNS 名稱。Compose 不會建立、刪除或重建 PostgreSQL。

`SPRING_JPA_HIBERNATE_DDL_AUTO` 預設可用 `update` 方便開發；正式資料庫 schema 穩定後建議改成 `validate`，避免啟動時自動修改資料庫結構。

## 正式部署

```bash
docker compose build
docker compose up -d
docker compose ps
```

正式前端：`http://NAS位址:5173`

正式部署請讓 `VITE_GATEWAY_URL` 保持空白。前端會透過同網域的 `/socket.io` 連到 Gateway，因此外網只需公開前端連接埠，不必另外公開 `GATEWAY_PORT`，也能避免 HTTPS mixed content。

若反向代理或防火牆直接公開 Gateway，請把實際前端網址加入 `ALLOWED_ORIGINS`，例如 `https://game.example.com`。一般 Docker Compose 部署只公開前端時，Nginx 會用同源 proxy 轉送 `/socket.io`、`/auth` 與 `/history`。

`GATEWAY_CONTAINER_PORT` 同時會提供給前端 Nginx proxy 使用；若修改此值，請重新建立 gateway 與 frontend 容器。

Backend 不會映射至 NAS 主機，只能由 Gateway 在 Docker 網路內存取。

每場遊戲結束時會在 game_records 保存唯一局號、房間、模式、勝方、回合、人數、開始／結束時間與完整結算 JSON；重複結算不會重複寫入。

## Debug 模式

只在可信任的測試環境使用。Debug API 和前端 Debug UI 需同時開啟：

```env
SPRING_PROFILES_ACTIVE=default
ENABLE_DEBUG_ACTIONS=true
VITE_ENABLE_DEBUG_MODE=true
```

```bash
docker compose up -d --build
```

Debug 工具會出現在同一個前端：`http://NAS位址:5173`。`fatcatkill-web-debug` 資料夾保留供參考，但環境建置不再啟動第二個前端。

## Windows 啟動方式

### 方法一：Docker Desktop

需求：

- Windows 10/11
- Docker Desktop（使用 Linux containers）
- 已存在且可連線的 PostgreSQL

在 PowerShell 進入專案根目錄：

```powershell
Copy-Item .env.example .env
notepad .env
```

確認 `.env` 的資料庫帳密與連線位置。若 PostgreSQL 跑在同一台 Windows 主機：

```env
SPRING_DATASOURCE_DOCKER_URL=jdbc:postgresql://host.docker.internal:5432/fatcatkill
ENABLE_DEBUG_ACTIONS=false
LOG_LEVEL=info
VITE_ENABLE_DEBUG_MODE=false
```

啟動正式環境：

```powershell
docker compose up -d --build
docker compose ps
```

開啟正式前端：`http://localhost:5173`

啟動 Debug 工具前，先將 `.env` 設為：

```env
ENABLE_DEBUG_ACTIONS=true
VITE_ENABLE_DEBUG_MODE=true
```

然後重新建置並啟動：

```powershell
docker compose up -d --build
```

Debug 工具會出現在同一個前端：`http://localhost:5173`

停止服務：

```powershell
docker compose down
```

### 方法二：Windows 本機開發

需求：

- JDK 17
- Maven 3.9+
- Node.js 20+
- 已啟動的 PostgreSQL

第一次執行先安裝 Node.js 依賴：

```powershell
Copy-Item .env.example .env
npm --prefix fatcatkill-gateway ci
npm --prefix fatcatkill-web ci
```

編輯 `.env`，讓本機 Backend 連到 PostgreSQL：

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fatcatkill
POSTGRES_USERNAME=postgres
POSTGRES_PASSWORD=your_password
ENABLE_DEBUG_ACTIONS=false
LOG_LEVEL=info
VITE_ENABLE_DEBUG_MODE=false
```

從專案根目錄啟動全部服務：

```powershell
.\start.bat
```

`start.bat` 會分別開啟 Backend、Gateway 與同一個前端視窗，但不會安裝依賴或啟動 PostgreSQL。

本機位址：

- 正式前端：`http://localhost:5173`
- Gateway：`http://localhost:8091`
- Backend：`http://localhost:8090`

需要 Debug 工具時，將 `.env` 的 `ENABLE_DEBUG_ACTIONS` 與 `VITE_ENABLE_DEBUG_MODE` 都改為 `true`，關閉原本三個服務視窗後重新執行 `start.bat`。

## 測試

Backend 測試使用 H2，不會連線或修改正式 PostgreSQL：

```bash
cd fatcatkill-api
mvn test
```

Gateway 測試會檢查路由白名單、房間身分宣告與資料遮蔽：

```bash
cd fatcatkill-gateway
npm test
```

前端測試會檢查角色池、翻譯與能力提示是否一致；建置用來確認正式 bundle 可產生：

```bash
cd fatcatkill-web
npm ci
npm test
npm run build
```
