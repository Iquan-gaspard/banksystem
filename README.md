# 金融商品喜好紀錄系統（banksystem）

使用者可以新增、查詢、修改、刪除自己喜好的金融商品，系統自動計算總手續費與預計扣款總金額。

- 前端：Vue 3 + Vite
- 後端：Java 17、Spring Boot 3.5、Maven Wrapper
- 資料庫：MySQL 8.4（所有存取皆透過 Stored Procedure）
- Web Server：Nginx；部署：Docker Compose

> 本專案開發過程使用 AI 工具輔助，包含規格整理、程式撰寫與測試；所有設計決策與驗證結果皆經本人確認。

---

## 目錄

1. [系統架構](#1-系統架構)
2. [快速啟動（Docker）](#2-快速啟動docker)
3. [非 Docker 啟動方式](#3-非-docker-啟動方式)
4. [測試](#4-測試)
5. [RESTful API](#5-restful-api)
6. [資料庫設計](#6-資料庫設計)
7. [計算規則](#7-計算規則)
8. [Transaction 設計](#8-transaction-設計)
9. [安全性：SQL Injection 與 XSS](#9-安全性sql-injection-與-xss)
10. [專案結構與後端分層](#10-專案結構與後端分層)
11. [設計決策](#11-設計決策)
12. [需求對照表](#12-需求對照表)

---

## 1. 系統架構

三層式架構：

```
Browser ──HTTP──▶ Nginx（Web Server，:8088）
                    ├─ /       → Vue 靜態檔
                    └─ /api/*  → Spring Boot（Application Server，:8080，僅內部網路）
                                    └─ JDBC CALL sp_xxx(?) → MySQL（RDBMS，:3306，僅內部網路）
```

- 對外只開放 Nginx；後端與資料庫只在 Docker 內部網路（資料庫另綁 `127.0.0.1:13306` 供本機除錯）。
- 前後端同源（都經 Nginx），不需要 CORS。

## 2. 快速啟動（Docker）

需求：Docker Desktop（含 Docker Compose）。

```bash
# 1. 建立環境變數檔（可修改密碼與 port）
cp .env.example .env          # Windows PowerShell：Copy-Item .env.example .env

# 2. 建置並啟動 db / backend / web 三個服務
docker compose up -d --build

# 3. 開啟瀏覽器
#    http://localhost:8088
```

第一次啟動時，MySQL 會依序自動執行 `DB/` 內的腳本（建表 → Stored Procedure → 示範資料 → 應用程式帳號）。

| 指令 | 說明 |
|---|---|
| `docker compose ps` | 查看服務狀態 |
| `docker compose logs -f backend` | 查看後端 log |
| `docker compose down` | 停止服務（**保留**資料） |
| `docker compose down -v` | 停止服務並**刪除資料庫 volume**（重設資料庫） |

> **重設資料庫必須使用 `docker compose down -v`**：`DB/` 內的初始化腳本只會在資料庫 volume 為空時執行，單純 `down` / `up` 不會重跑腳本。

`.env` 可調整的設定：

| 變數 | 預設 | 說明 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `change_me_root` | MySQL root 密碼 |
| `APP_DB_USER` / `APP_DB_PASSWORD` | `banksystem_app` / `change_me_app` | 應用程式帳號（只有 EXECUTE 權限） |
| `WEB_PORT` | `8088` | 網站對外 port（避開常被佔用的 80） |
| `DB_HOST_PORT` | `13306` | 本機連 MySQL 的 port（避開 3306） |

## 3. 非 Docker 啟動方式

需求：JDK 17、Node.js 20+、MySQL 8。

**(1) 建立資料庫**

```bash
mysql -uroot -p < DB/01_schema.sql
mysql -uroot -p < DB/02_procedures.sql
mysql -uroot -p < DB/03_seed.sql
```

再以 root 建立應用程式帳號，只授予 EXECUTE。`DB/04_app_user.sh` 是給 Docker 初始化用的 shell 腳本（Windows 無法直接執行），非 Docker 環境請改用以下等效 SQL：

```sql
CREATE USER 'banksystem_app'@'%' IDENTIFIED BY '<你的密碼>';
GRANT EXECUTE ON banksystem.* TO 'banksystem_app'@'%';
```

**(2) 啟動後端**（預設連 `localhost:3306`）

```bash
cd backend
# macOS / Linux
DB_PASSWORD='<你的密碼>' ./mvnw spring-boot:run
# Windows PowerShell
$env:DB_PASSWORD='<你的密碼>'; .\mvnw.cmd spring-boot:run
```

可用環境變數：`DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD`。

**(3) 啟動前端**

```bash
cd frontend
npm install
npm run dev
# 開啟 http://localhost:5173（Vite 會把 /api 轉發到 localhost:8080）
```

## 4. 測試

```bash
cd backend
./mvnw clean verify        # Windows：.\mvnw.cmd clean verify
```

| 測試 | 類型 | 內容 |
|---|---|---|
| `FeeCalculatorTest` | 單元 | 手續費 / 總金額計算、四捨五入邊界、上限檢查 |
| `LikeServiceTest` | 單元（Mockito） | 業務流程：使用者不存在、資料不存在或不屬於該使用者 |
| `LikeControllerTest` | 展示層（`@WebMvcTest`） | 201 / 204 / 400 / 404、各欄位邊界值（剛好合法 / 超出一格）、數量為小數、XSS 輸入、竄改金額欄位、惡意 userId、錯誤訊息不回傳使用者輸入 |
| `LikeTransactionIntegrationTest` | 整合（Testcontainers + 真實 MySQL） | 經 SP 的 CRUD、**多表交易 rollback**、應用程式帳號無法直接存取資料表 |

整合測試會啟動 MySQL 容器並執行 `DB/` 內的腳本；**沒有 Docker 的環境會自動略過**（`@Testcontainers(disabledWithoutDocker = true)`），`./mvnw clean verify` 仍可通過。

## 5. RESTful API

Base path：`/api`，回應格式 JSON；錯誤統一為 RFC 7807 `application/problem+json`。

| Method | Path | 說明 | 成功 | 失敗 |
|---|---|---|---|---|
| GET | `/api/users` | 使用者清單 | 200 | — |
| GET | `/api/users/{userId}` | 單一使用者 | 200 | 400、404 |
| GET | `/api/users/{userId}/likes` | 喜好清單 | 200 | 400、404 |
| GET | `/api/users/{userId}/likes/{sn}` | 單筆喜好 | 200 | 400、404 |
| POST | `/api/users/{userId}/likes` | 新增喜好 | 201 + `Location` | 400、404 |
| PUT | `/api/users/{userId}/likes/{sn}` | 修改喜好 | 200 | 400、404 |
| DELETE | `/api/users/{userId}/likes/{sn}` | 刪除喜好 | 204 | 400、404 |

新增 / 修改的 request body（總手續費、總金額由後端計算，**傳入會被拒絕**）：

```json
{
  "productName": "全球債券基金A",
  "price": 1000.00,
  "feeRate": 0.01,
  "purchaseQuantity": 3,
  "account": "1111999666"
}
```

回應：

```json
{
  "sn": 1, "productNo": 1, "productName": "全球債券基金A",
  "price": 1000.00, "feeRate": 0.0100, "purchaseQuantity": 3,
  "account": "1111999666", "totalFee": 30.00, "totalAmount": 3030.00,
  "email": "test@email.com"
}
```

驗證失敗範例：

```json
{
  "type": "about:blank", "title": "Bad Request", "status": 400,
  "detail": "輸入資料格式錯誤",
  "errors": { "price": "產品價格必須大於 0", "productName": "不可包含 < 或 > 字元" }
}
```

輸入規則：產品名稱 1–100 字且不可含 `<` `>`；價格 > 0（最多 2 位小數）；手續費率 0–1（最多 4 位小數，0.01 = 1%）；購買數量 1–1,000,000 的整數；扣款帳號 10–16 碼數字；userId 1–20 碼英數字。

## 6. 資料庫設計

DDL / DML 位於 [`DB/`](DB/)：

| 檔案 | 內容 |
|---|---|
| `01_schema.sql` | 建立資料庫與資料表（PK、FK、CHECK、索引） |
| `02_procedures.sql` | 7 個 Stored Procedure |
| `03_seed.sql` | 示範資料（3 位使用者、3 筆喜好） |
| `04_app_user.sh` | 建立應用程式帳號，只授予 EXECUTE 權限 |

```
users (user_id PK, user_name, email, account)
   1
   │
   N
like_list (sn PK, user_id FK, product_no FK UNIQUE, purchase_quantity,
           account, total_fee DECIMAL(18,2), total_amount DECIMAL(18,2))
   1
   │
   1
product (no PK, product_name, price DECIMAL(15,2), fee_rate DECIMAL(5,4))
```

- 金額一律使用 `DECIMAL`（Java 端 `BigDecimal`），不用浮點數。
- 每筆喜好對應一筆自己的產品資料（1:1），修改 / 刪除不會影響其他人。
- CHECK：價格 > 0、費率 0–1、數量 > 0、金額 ≥ 0。

Stored Procedure：

| SP | 動到的表 | 說明 |
|---|---|---|
| `sp_user_list` | users | 使用者清單 |
| `sp_user_get` | users | 單一使用者 |
| `sp_like_list_by_user` | like_list ⋈ product ⋈ users | 喜好清單（含 Email） |
| `sp_like_get` | like_list ⋈ product ⋈ users | 單筆喜好 |
| `sp_like_create` | **product → like_list** | 新增，回傳新 sn |
| `sp_like_update` | **product + like_list** | 修改，以 user_id + sn 定位 |
| `sp_like_delete` | **like_list → product** | 刪除，以 user_id + sn 定位 |

## 7. 計算規則

新台幣，四捨五入（HALF_UP）至整數元：

```
小計          = 價格 × 購買數量
總手續費      = round(小計 × 手續費率)
預計扣款總金額 = round(小計) + 總手續費
```

| 價格 | 數量 | 費率 | 總手續費 | 預計扣款總金額 |
|---|---|---|---|---|
| 1000 | 3 | 0.01 | 30 | 3030 |
| 35.50 | 1000 | 0.0015 | 53（53.25） | 35553 |
| 12.34 | 7 | 0.015 | 1（1.2957） | 87 |

計算位於業務層 [`FeeCalculator`](backend/src/main/java/com/banksystem/service/FeeCalculator.java)，前端傳入的金額一律不採用。

## 8. Transaction 設計

新增、修改、刪除都會**同時異動 `product` 與 `like_list` 兩張表**，由業務層 `@Transactional` 包成一個交易：任何一步失敗（例如違反 CHECK / FK）就整筆 rollback。

- SP 內刻意**不寫** `START TRANSACTION / COMMIT`：MySQL 在已開啟的交易中執行 `START TRANSACTION` 會隱含 commit 外層交易，造成無法整筆 rollback。因此交易邊界統一由 Spring 管理。
- 取捨：若繞過後端直接在 MySQL client 以 autocommit 呼叫 SP，失敗時不會自動 rollback；應用程式的所有呼叫皆在交易內。
- 整合測試 `LikeTransactionIntegrationTest` 以真實 MySQL 驗證：故意讓第二張表寫入失敗，確認第一張表的異動一併 rollback。

## 9. 安全性：SQL Injection 與 XSS

**SQL Injection**

1. 所有資料庫存取都透過 Stored Procedure，Java 端以 `SimpleJdbcCall`（CallableStatement）具名綁定參數，**程式中沒有任何字串拼接的 SQL**。
2. SP 內不使用動態 SQL（`PREPARE` / `CONCAT`）。
3. 最小權限：應用程式帳號只有 `EXECUTE` 權限，無法直接 `SELECT / INSERT / UPDATE / DELETE` 資料表（整合測試驗證）。
4. 輸入白名單驗證（userId 英數字、帳號純數字、數值範圍），不合法直接 400。

**XSS**

1. 前端一律用 Vue `{{ }}` 輸出（自動 HTML 跳脫），**全專案不使用 `v-html`**。
2. 後端 `@NoHtml` 驗證拒絕含 `<`、`>` 的文字輸入。
3. Nginx 回應 `Content-Security-Policy`（`script-src 'self'`，禁止 inline script）、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy`。
4. API 回應加 `nosniff`、`Cache-Control: no-store`；錯誤訊息不帶 stack trace、SQL 或使用者原始輸入。

**其他**

- 每筆資料都以 `user_id + sn` 定位，無法修改 / 刪除其他使用者的資料（回 404）。
- 不接受未定義的 JSON 欄位（防止竄改金額）；整數欄位收到小數直接拒絕，不默默截斷。
- 錯誤回應的 `detail` 一律為固定文字，不回傳使用者輸入。
- 密碼由 `.env` 提供，不寫在程式碼；`.env` 不進版控。
- 後端容器以非 root 使用者執行；Nginx 不顯示版本號。

**已知限制與正式上線前需補強**

本作業範圍內未實作、正式環境必須補上的項目：

| 項目 | 現況 | 正式環境做法 |
|---|---|---|
| 身分驗證 / 授權 | 無登入，userId 由網址指定，任何人可操作任一使用者的資料 | 導入登入機制（如 OAuth2 / JWT），userId 改由登入身分取得，不再信任網址參數 |
| CSRF | 目前無 cookie / session，不適用 | 若改用 cookie 驗證，需加 CSRF token 或 `SameSite` cookie |
| 傳輸加密 | HTTP | Nginx 設定 HTTPS（TLS）與 HSTS |
| 流量限制 | 無 | Nginx `limit_req` 或 API Gateway 限制請求頻率 |
| 稽核紀錄 | 無 | 記錄誰在何時新增 / 修改 / 刪除了哪筆資料 |
| 並發修改 | 最後寫入為準 | 加 version 欄位做樂觀鎖 |
| 機敏設定 | `.env` 檔 | 改用 Secret 管理服務（如 Vault）；`.env.example` 的預設密碼僅供本機示範，部署前必須更換 |
| 超大金額顯示 | 後端以 `BigDecimal` 精確計算；但金額超過約 9 千兆（JavaScript 數字精度上限 2⁵³）時，前端顯示可能失準 | API 改以字串傳遞金額，或前端使用精確小數函式庫 |

## 10. 專案結構與後端分層

```
├─ DB/                       # DDL、SP、DML、應用程式帳號
├─ backend/                  # Spring Boot（Maven Wrapper）
│  └─ src/main/java/com/banksystem/
│     ├─ controller/         # 展示層：REST API、參數驗證、HTTP 狀態碼
│     ├─ dto/                # 展示層：request / response 物件
│     ├─ service/            # 業務層：金額計算、業務規則、Transaction 邊界
│     ├─ repository/         # 資料層：呼叫 Stored Procedure
│     ├─ model/              # 資料層物件
│     └─ common/             # 共用層：例外處理、驗證規則、安全 header
├─ frontend/                 # Vue 3 + Vite、nginx.conf
├─ docker-compose.yml
└─ .env.example
```

| 層 | 職責 | 不做的事 |
|---|---|---|
| 展示層 | 接收 HTTP、`@Valid` 驗證、DTO 轉換、HTTP 狀態碼 | 不寫業務邏輯、不碰 DB |
| 業務層 | 金額計算、存在性檢查、`@Transactional` | 不碰 HTTP、不寫 SQL |
| 資料層 | 呼叫 SP、ResultSet 對應物件 | 不做業務判斷 |
| 共用層 | 全域例外處理（ProblemDetail）、`@NoHtml`、格式規則、安全 header | — |

依賴方向：controller → service → repository，三者皆可使用 common。

## 11. 設計決策

| 項目 | 決定 | 理由 |
|---|---|---|
| 產品與喜好的關係 | 1:1，新增喜好時一併建立產品 | 使用者自行輸入產品名稱 / 價格 / 費率；修改不會影響他人 |
| 使用者識別 | 前端下拉選單切換，userId 放在 URL | 範圍內無登入需求；正式環境應改由驗證機制（如 JWT）取得 |
| 扣款帳號 | 預設帶入使用者帳號，可修改 | 同一使用者可能用不同帳號扣款 |
| 金額進位 | 新台幣整數元、HALF_UP | 扣款以整數元計 |
| 購買數量 | 整數 | — |
| 刪除 | 實體刪除 | 無保留歷史需求 |
| Transaction | Spring `@Transactional`，SP 不含交易語句 | 見第 8 節 |
| 金額計算位置 | Java 業務層 | 符合分層、可單元測試 |
| DB 存取 | Spring JDBC `SimpleJdbcCall` | 呼叫 SP 最直接、參數化 |
| 並發修改 | 未處理（最後寫入為準） | 範圍外；可加 version 欄位做樂觀鎖 |

## 12. 需求對照表

| 需求 | 實作位置 |
|---|---|
| 新增喜好金融商品 | `POST /api/users/{userId}/likes` → [`LikeController`](backend/src/main/java/com/banksystem/controller/LikeController.java) → `LikeService.create` → `sp_like_create`；前端 [`LikeForm.vue`](frontend/src/components/LikeForm.vue) |
| 查詢喜好清單（產品名稱、扣款帳號、總金額、總手續費、Email） | `GET /api/users/{userId}/likes` → `sp_like_list_by_user`；前端 [`LikeTable.vue`](frontend/src/components/LikeTable.vue)、[`App.vue`](frontend/src/App.vue) |
| 刪除喜好金融商品 | `DELETE /api/users/{userId}/likes/{sn}` → `sp_like_delete` |
| 修改喜好金融商品 | `PUT /api/users/{userId}/likes/{sn}` → `sp_like_update` |
| Web Server + Application Server + RDBMS 三層式架構 | [`docker-compose.yml`](docker-compose.yml)：Nginx → Spring Boot → MySQL |
| 後端分展示層、業務層、資料層、共用層 | 第 10 節 |
| Vue.js 前端 | [`frontend/`](frontend/) |
| Spring Boot | [`backend/`](backend/) |
| RESTful API | 第 5 節 |
| Maven / Gradle | Maven Wrapper（[`backend/mvnw`](backend/mvnw)） |
| 透過 Stored Procedure 存取資料庫 | [`DB/02_procedures.sql`](DB/02_procedures.sql)、[`repository/`](backend/src/main/java/com/banksystem/repository/) |
| 多表異動實作 Transaction | [`LikeService`](backend/src/main/java/com/banksystem/service/LikeService.java) `@Transactional`；第 8 節 |
| DDL / DML 放在 DB 資料夾 | [`DB/`](DB/) |
| 防 SQL Injection、XSS | 第 9 節 |
