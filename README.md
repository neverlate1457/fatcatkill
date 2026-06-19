# FatCatKill

FatCatKill 是由 Spring Boot API、Socket.IO Gateway、Vue 前端與既有 PostgreSQL 組成的多人遊戲。

## 服務

- `fatcatkill-api`: 遊戲規則、房間狀態與 PostgreSQL 持久化
- `fatcatkill-gateway`: WebSocket、房間廣播、玩家身分綁定與 API 白名單
- `fatcatkill-web`: 正式玩家前端
- `fatcatkill-web-debug`: 測試角色與 Debug 工具，只在 Debug profile 啟用

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
GATEWAY_PORT=8091
GATEWAY_CONTAINER_PORT=3000
FRONTEND_PORT=5173
VITE_GATEWAY_URL=http://localhost:8091
SPRING_PROFILES_ACTIVE=default
ENABLE_DEBUG_ACTIONS=false
```

`host.docker.internal` 代表同一台 NAS 主機。如果 PostgreSQL 位於其他主機，請改成該主機 IP 或 DNS 名稱。Compose 不會建立、刪除或重建 PostgreSQL。

## 正式部署

```bash
docker compose build
docker compose up -d
docker compose ps
```

正式前端：`http://NAS位址:5173`

Backend 不會映射至 NAS 主機，只能由 Gateway 在 Docker 網路內存取。

每場遊戲結束時會在 game_records 保存唯一局號、房間、模式、勝方、回合、人數、開始／結束時間與完整結算 JSON；重複結算不會重複寫入。

## Debug 模式

只在可信任的測試環境使用：

```env
SPRING_PROFILES_ACTIVE=default
ENABLE_DEBUG_ACTIONS=true
```

```bash
docker compose --profile debug up -d --build
```

Debug 前端：`http://NAS位址:5174`

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
```

啟動正式環境：

```powershell
docker compose up -d --build
docker compose ps
```

開啟正式前端：`http://localhost:5173`

啟動 Debug 環境前，先將 `.env` 設為：

```env
ENABLE_DEBUG_ACTIONS=true
```

然後執行：

```powershell
docker compose --profile debug up -d --build
```

Debug 前端：`http://localhost:5174`

停止服務：

```powershell
docker compose --profile debug down
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
npm --prefix fatcatkill-web-debug ci
```

編輯 `.env`，讓本機 Backend 連到 PostgreSQL：

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fatcatkill
POSTGRES_USERNAME=postgres
POSTGRES_PASSWORD=your_password
ENABLE_DEBUG_ACTIONS=false
```

從專案根目錄啟動全部服務：

```powershell
.\start.bat
```

`start.bat` 會分別開啟 Backend、Gateway、正式前端與 Debug 前端視窗，但不會安裝依賴或啟動 PostgreSQL。

本機位址：

- 正式前端：`http://localhost:5173`
- Debug 前端：`http://localhost:5174`
- Gateway：`http://localhost:8091`
- Backend：`http://localhost:8090`

需要 Debug API 時，將 `.env` 的 `ENABLE_DEBUG_ACTIONS` 改為 `true`，關閉原本四個服務視窗後重新執行 `start.bat`。

## 測試

Backend 測試使用 H2，不會連線或修改正式 PostgreSQL：

```bash
cd fatcatkill-api
mvn test
```

前端建置：

```bash
cd fatcatkill-web
npm ci
npm run build
```