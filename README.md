# 金融商品喜好紀錄系統（banksystem）

[![CI](https://github.com/Iquan-gaspard/banksystem/actions/workflows/ci.yml/badge.svg)](https://github.com/Iquan-gaspard/banksystem/actions/workflows/ci.yml)

使用者登入後，可以新增、查詢、修改、刪除自己喜好的金融商品，系統自動計算總手續費與預計扣款總金額，並記錄每一次異動。

- 前端：Vue 3 + Vite
- 後端：Java 17、Spring Boot 3.5、Spring Security、Maven Wrapper
- 資料庫：MySQL 8.4（所有存取皆透過 Stored Procedure）
- Web Server：Nginx；部署：Docker Compose；CI：GitHub Actions

> 本專案開發過程使用 AI 工具輔助，包含規格整理、程式撰寫與測試；所有設計決策與驗證結果皆經本人確認。

### 5 分鐘快速體驗

只需要 Git 與 Docker Desktop（詳見[快速啟動](#2-快速啟動docker)）：

```bash
git clone https://github.com/Iquan-gaspard/banksystem.git && cd banksystem
cp .env.example .env                  # PowerShell：Copy-Item .env.example .env
docker compose up -d --build --wait   # 第一次約需數分鐘
```

開啟 http://localhost:8088 後，建議依序操作：

1. 以 `A1236456789` / `Demo@1234` 登入，看到 2 筆喜好商品與合計金額。
2. 按「新增喜好商品」：輸入價格 `1000`、費率 `0.01`、數量 `3`，系統算出總手續費 30、預計扣款 3,030。
3. 按「編輯」修改數量，金額自動重算；按「刪除」移除資料。
4. 按「操作紀錄 → 顯示」，可看到每次新增 / 修改 / 刪除的前後內容、執行者與來源 IP。
5. 登出，改以 `B2345678901` 登入，只看得到自己的資料（無法存取他人資料）。

結束後執行 `docker compose down -v` 停止並清除資料。

---

## 目錄

1. [系統架構](#1-系統架構)
2. [快速啟動（Docker）](#2-快速啟動docker)
3. [非 Docker 啟動方式](#3-非-docker-啟動方式)
4. [啟用 HTTPS（選用）](#4-啟用-https選用)
5. [測試與 CI](#5-測試與-ci)
6. [RESTful API](#6-restful-api)
7. [資料庫設計](#7-資料庫設計)
8. [計算規則](#8-計算規則)
9. [Transaction 設計](#9-transaction-設計)
10. [安全性](#10-安全性)
11. [專案結構與後端分層](#11-專案結構與後端分層)
12. [設計決策](#12-設計決策)
13. [需求對照表](#13-需求對照表)

---

## 1. 系統架構

三層式架構：

```
Browser ──HTTP(S)──▶ Nginx（Web Server，:8088）
                       ├─ 流量限制（登入 5 次/分、API 20 次/秒）
                       ├─ /       → Vue 靜態檔
                       └─ /api/*  → Spring Boot（Application Server，:8080，僅內部網路）
                                      ├─ Spring Security：Session 登入、授權、CSRF
                                      └─ JDBC CALL sp_xxx(?) → MySQL（RDBMS，:3306，僅內部網路）
```

- 對外只開放 Nginx；後端與資料庫只在 Docker 內部網路（資料庫另綁 `127.0.0.1:13306` 供本機除錯）。
- 啟動順序由健康檢查控制：MySQL healthy → Spring Boot healthy（`/actuator/health`，含 DB 檢查）→ Nginx。

## 2. 快速啟動（Docker）

需求：Git、[Docker Desktop](https://www.docker.com/products/docker-desktop/)（已內含 Docker Compose）。**不需要**另外安裝 Java、Maven、Node.js 或 MySQL，建置都在容器內完成。

```bash
# 0. 下載專案，並確認 Docker Desktop 已開啟（docker version 可看到 Server 資訊）
git clone https://github.com/Iquan-gaspard/banksystem.git
cd banksystem

# 1. 建立環境變數檔（可修改密碼與 port）
cp .env.example .env          # Windows PowerShell：Copy-Item .env.example .env

# 2. 建置並啟動 db / backend / web（--wait 會等到全部 healthy）
#    第一次需下載映像檔與 Maven / npm 套件，約需數分鐘
docker compose up -d --build --wait

# 3. 開啟瀏覽器 http://localhost:8088，以下方示範帳號登入
```

若 8088 已被佔用，修改 `.env` 的 `WEB_PORT` 後重新執行步驟 2。

**示範帳號**（密碼皆為 `Demo@1234`，資料庫中以 BCrypt 雜湊儲存）：

| 使用者 ID | 名稱 | 說明 |
|---|---|---|
| `A1236456789` | 王o明 | 有 2 筆示範資料 |
| `B2345678901` | 李o華 | 有 1 筆示範資料 |
| `C3456789012` | 陳o美 | 無資料 |

API 文件（Swagger UI）：http://localhost:8088/api/swagger-ui.html（先在網站登入，同一瀏覽器即可試用 API）。

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
| `WEB_BIND` | `127.0.0.1` | 網站監聽的介面：預設只有本機能連；需讓區網其他電腦連入時設為 `0.0.0.0` |
| `WEB_PORT` | `8088` | 網站對外 port（避開常被佔用的 80） |
| `DB_HOST_PORT` | `13306` | 本機連 MySQL 的 port（避開 3306） |
| `SWAGGER_ENABLED` | `true` | 是否開放 API 文件；正式環境請設為 `false` |

> `.env.example` 的密碼僅供本機示範，部署前必須更換；真正的 `.env` 不進版控。

## 3. 非 Docker 啟動方式

需求：JDK 17、Node.js 20+、MySQL 8。

**(1) 建立資料庫**

> ⚠️ `DB/01_schema.sql` 用於**空白資料庫初始化**，會先 DROP 既有資料表；對已有資料的資料庫重複執行會清除所有資料（含操作紀錄）。正式環境的結構異動應改用 migration 工具（如 Flyway / Liquibase）。

在專案根目錄執行（使用 `source`，Windows PowerShell 與 macOS / Linux 皆適用；PowerShell 不支援 `<` 重新導向）：

```bash
mysql -uroot -p --default-character-set=utf8mb4 -e "source DB/01_schema.sql; source DB/02_procedures.sql; source DB/03_seed.sql;"
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

可用環境變數：`DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD`、`COOKIE_SECURE`、`SWAGGER_ENABLED`。

**(3) 啟動前端**

```bash
cd frontend
npm install
npm run dev
# 開啟 http://localhost:5173（Vite 會把 /api 轉發到 localhost:8080）
```

非 Docker 模式沒有 Nginx，因此不含流量限制；正式部署請使用 Nginx（`frontend/nginx.conf`）。

## 4. 啟用 HTTPS（選用）

預設為 HTTP（本機示範用自簽憑證會讓瀏覽器顯示警告）。專案提供已驗證過的設定範本：

| 檔案 | 內容 |
|---|---|
| `frontend/nginx.https.conf.example` | HTTP 轉址 HTTPS、TLS 1.2 / 1.3、HSTS，其餘（流量限制、安全 header）同 `nginx.conf` |
| `docker-compose.https.example.yml` | 掛載憑證、開放 443、後端 `COOKIE_SECURE=true`（Session cookie 只在 HTTPS 傳送） |

```bash
# 1. 放置憑證（certs/ 已列入 .gitignore，私鑰不可提交）
#    certs/fullchain.pem、certs/privkey.pem
# 2. 以覆寫檔啟動
docker compose -f docker-compose.yml -f docker-compose.https.example.yml up -d --build --wait
```

正式環境常見做法是由負載平衡器或 API Gateway 終結 TLS，此時保留 `X-Forwarded-Proto` 轉送即可（後端已設定 `forward-headers-strategy: native`）。

## 5. 測試與 CI

```bash
cd backend
./mvnw clean verify        # Windows：.\mvnw.cmd clean verify
```

後端共 54 個測試：

| 測試 | 類型 | 內容 |
|---|---|---|
| `FeeCalculatorTest` | 單元 | 手續費 / 總金額計算、四捨五入邊界、上限檢查 |
| `LikeServiceTest` | 單元（Mockito） | 業務流程：使用者不存在、資料不存在或不屬於該使用者 |
| `LikeControllerTest` | 展示層（`@WebMvcTest` + Spring Security） | 新增 / 查詢 / 修改 / 刪除的 200 / 201 / 204 / 400 / 404；未登入 401、存取或修改他人資料 403、缺少 CSRF 403；各欄位邊界值；XSS 輸入；竄改金額欄位；金額以字串輸入輸出不失精度；錯誤訊息不回傳使用者輸入 |
| `LikeTransactionIntegrationTest` | 整合（Testcontainers + 真實 MySQL） | 以示範帳號實際登入；**經 HTTP 完整走過登入 → 新增 → 查詢 → 修改 → 刪除 → 操作紀錄**；經 SP 的 CRUD；**多表交易 rollback**（含操作紀錄）；應用程式帳號無法直接存取資料表 |

需要 JDK 17（`JAVA_HOME` 指向 JDK 17）。Maven Wrapper 第一次執行會自動從 Maven Central 下載 Maven 3.10.0，因此需要能連線到 `repo.maven.apache.org`；在無法連外或限制 PowerShell 執行的環境中，可改用已安裝的 Maven 執行 `mvn clean verify`，或以 Docker 建置（`docker compose build backend`，不需要本機 JDK）。

整合測試會啟動 MySQL 容器並執行 `DB/` 內的腳本；**沒有 Docker 的環境會自動略過**（`@Testcontainers(disabledWithoutDocker = true)`），`./mvnw clean verify` 仍可通過。

前端共 29 個測試（Vitest + Vue Test Utils，於 jsdom 執行，不需要瀏覽器或後端）：

| 測試 | 內容 |
|---|---|
| `decimal.test.js` | 金額精確加總與格式化（含超過 2⁵³ 的金額）、操作紀錄欄位比對規則 |
| `LikeTable.test.js` | 畫面上每筆金額與合計列（總手續費、預計扣款總金額）；大金額合計不失真；編輯 / 刪除事件；HTML 以純文字顯示（XSS） |
| `AuditLogList.test.js` | 扣款帳號前導 0、產品名稱「1」→「01」能顯示為異動；多欄位異動；數值僅格式不同顯示「內容未變更」 |
| `LikeForm.test.js` | 新增帶入預設帳號、送出內容（價格與費率為字串）；編輯帶入原資料；後端欄位錯誤顯示；儲存中與取消 |
| `App.test.js` | 以模擬後端（含 Session 與 CSRF 規則）測試：登入失敗訊息；登入後新增 → 修改 → 刪除與畫面更新；驗證失敗時表單保留並顯示錯誤；登出後重新登入第一次即成功 |

```bash
cd frontend
npm install
npm test
```

**GitHub Actions**（`.github/workflows/ci.yml`）每次推送自動執行：

1. 後端 `./mvnw verify`（含 Testcontainers 整合測試）
2. 前端 `npm test`（工具函式、Vue 元件與畫面流程測試）與 `npm run build`
3. `docker compose up --wait` 啟動整套系統，以 curl 實際登入、驗證授權，並經 HTTP 完整執行新增 → 查詢 → 修改 → 刪除 → 操作紀錄（冒煙測試）

## 6. RESTful API

Base path：`/api`，回應格式 JSON；錯誤統一為 RFC 7807 `application/problem+json`。完整文件見 Swagger UI（`/api/swagger-ui.html`）。

**登入 / 登出**

| Method | Path | 說明 | 成功 | 失敗 |
|---|---|---|---|---|
| POST | `/api/auth/login` | 登入（表單欄位 `userId`、`password`），建立 Session | 200 + 使用者資料 | 401、403（缺 CSRF）、429（過於頻繁） |
| POST | `/api/auth/logout` | 登出 | 204 | — |
| GET | `/api/auth/me` | 目前登入的使用者 | 200 | 401 |

**業務 API**（`{userId}` 必須是登入者本人，否則 403；未登入 401）

| Method | Path | 說明 | 成功 | 失敗 |
|---|---|---|---|---|
| GET | `/api/users/{userId}` | 使用者資料 | 200 | 401、403 |
| GET | `/api/users/{userId}/likes` | 喜好清單 | 200 | 401、403 |
| GET | `/api/users/{userId}/likes/{sn}` | 單筆喜好 | 200 | 400、401、403、404 |
| POST | `/api/users/{userId}/likes` | 新增喜好 | 201 + `Location` | 400、401、403 |
| PUT | `/api/users/{userId}/likes/{sn}` | 修改喜好 | 200 | 400、401、403、404 |
| DELETE | `/api/users/{userId}/likes/{sn}` | 刪除喜好 | 204 | 401、403、404 |
| GET | `/api/users/{userId}/audit-logs` | 操作紀錄（最新 50 筆） | 200 | 401、403 |

**CSRF**：非 GET 請求須帶 `X-XSRF-TOKEN` header，值取自後端寫入的 `XSRF-TOKEN` cookie（前端已自動處理）。

新增 / 修改的 request body（總手續費、總金額由後端計算，**傳入會被拒絕**）：

```json
{
  "productName": "全球債券基金A",
  "price": "1000.00",
  "feeRate": "0.01",
  "purchaseQuantity": 3,
  "account": "1111999666"
}
```

價格、手續費率可傳數字或十進位字串（建議字串，避免用戶端浮點數誤差）。

回應（金額與費率一律為十進位字串，避免 JavaScript `Number` 超過 2⁵³ 時失去精度；前端以 BigInt 精確加總與顯示）：

```json
{
  "sn": 1, "productNo": 1, "productName": "全球債券基金A",
  "price": "1000.00", "feeRate": "0.0100", "purchaseQuantity": 3,
  "account": "1111999666", "totalFee": "30.00", "totalAmount": "3030.00",
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

輸入規則：產品名稱 1–100 字且不可含 `<` `>`；價格 > 0（最多 2 位小數）；手續費率 0–1（最多 4 位小數，0.01 = 1%）；購買數量 1–1,000,000 的整數（小數直接拒絕）；扣款帳號 10–16 碼數字。

## 7. 資料庫設計

DDL / DML 位於 [`DB/`](DB/)：

| 檔案 | 內容 |
|---|---|
| `01_schema.sql` | 建立資料庫與資料表（PK、FK、CHECK、索引） |
| `02_procedures.sql` | Stored Procedure 與快照函式 |
| `03_seed.sql` | 示範資料（3 位使用者、3 筆喜好） |
| `04_app_user.sh` | 建立應用程式帳號，只授予 EXECUTE 權限（帳號名稱限英數底線；密碼先跳脫再放入 SQL，可含任意特殊字元） |

```
users (user_id PK, user_name, email, account, password_hash)
   1
   │
   N
like_list (sn PK, user_id FK, product_no FK UNIQUE, purchase_quantity,
           account, total_fee DECIMAL(18,2), total_amount DECIMAL(18,2))
   1
   │
   1
product (no PK, product_name, price DECIMAL(15,2), fee_rate DECIMAL(5,4))

audit_log (id PK, actor_user_id FK, owner_user_id FK, action, like_sn,
           before_data JSON, after_data JSON, client_ip, created_at)
```

- 金額一律使用 `DECIMAL`（Java 端 `BigDecimal`），不用浮點數。
- 每筆喜好對應一筆自己的產品資料（1:1），修改 / 刪除不會影響其他人。
- 密碼只存 BCrypt 雜湊，不存明文。
- `audit_log.like_sn` 刻意不設 FK：資料刪除後紀錄仍需保留。
- CHECK：價格 > 0、費率 0–1、數量 > 0、金額 ≥ 0、動作只能是 CREATE / UPDATE / DELETE。

Stored Procedure：

| SP | 動到的表 | 說明 |
|---|---|---|
| `sp_user_get` | users | 使用者資料 |
| `sp_user_get_credentials` | users | 登入驗證用（帳號與密碼雜湊，不經 API 回傳） |
| `sp_like_list_by_user` | like_list ⋈ product ⋈ users | 喜好清單（含 Email） |
| `sp_like_get` | like_list ⋈ product ⋈ users | 單筆喜好 |
| `sp_like_create` | **product → like_list → audit_log** | 新增，回傳新 sn |
| `sp_like_update` | **product + like_list + audit_log** | 修改，以 user_id + sn 定位 |
| `sp_like_delete` | **like_list → product + audit_log** | 刪除，以 user_id + sn 定位 |
| `sp_audit_list_by_user` | audit_log | 操作紀錄 |
| `fn_like_snapshot`（函式） | like_list ⋈ product | 產生異動前 / 後的 JSON 快照 |

## 8. 計算規則

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

## 9. Transaction 設計

新增、修改、刪除都會**同時異動 `product`、`like_list`、`audit_log` 三張表**，由業務層 `@Transactional` 包成一個交易：任何一步失敗（例如違反 CHECK / FK）就整筆 rollback，**操作紀錄也一併還原**，不會留下「資料沒改成功、卻有紀錄」的情況。

- SP 內刻意**不寫** `START TRANSACTION / COMMIT`：MySQL 在已開啟的交易中執行 `START TRANSACTION` 會隱含 commit 外層交易，造成無法整筆 rollback。因此交易邊界統一由 Spring 管理。
- 取捨：若繞過後端直接在 MySQL client 以 autocommit 呼叫 SP，失敗時不會自動 rollback；應用程式的所有呼叫皆在交易內。
- 整合測試 `LikeTransactionIntegrationTest` 以真實 MySQL 驗證：故意讓第二張表寫入失敗，確認其他表的異動與操作紀錄一併 rollback。

## 10. 安全性

**登入與授權**

1. Spring Security Session 登入；密碼以 BCrypt 比對；帳號查詢同樣透過 Stored Procedure。
2. 登入失敗一律回「帳號或密碼錯誤」，不透露是帳號不存在還是密碼錯誤。
3. Session cookie：`HttpOnly`（JavaScript 讀不到）、`SameSite=Lax`、15 分鐘逾時；啟用 HTTPS 時加上 `Secure`。
4. 授權：`/api/users/{userId}/**` 只允許登入者本人（他人 403、未登入 401），不信任網址參數。
5. 不提供「列出所有使用者」的 API，避免洩漏他人 Email 與帳號。
6. 選擇 Session 而非 JWT：token 不需存放在 JavaScript 可讀的位置，可降低 XSS 竊取風險；登出即可讓 Session 失效。

**CSRF**

- Cookie-to-header：後端寫入 `XSRF-TOKEN` cookie，前端在非 GET 請求帶 `X-XSRF-TOKEN` header；其他網站讀不到本站 cookie，因此偽造請求會被拒絕（403）。登入請求同樣受保護。
- 搭配 `SameSite=Lax` 作為第二層防護。

**SQL Injection**

1. 所有資料庫存取都透過 Stored Procedure，Java 端以 `SimpleJdbcCall`（CallableStatement）具名綁定參數，**程式中沒有任何字串拼接的 SQL**。
2. SP 內不使用動態 SQL（`PREPARE` / `CONCAT`）。
3. 最小權限：應用程式帳號只有 `EXECUTE` 權限，無法直接 `SELECT / INSERT / UPDATE / DELETE` 資料表（整合測試驗證）。
4. 輸入白名單驗證（帳號純數字、數值範圍），不合法直接 400；惡意 userId 在授權層即被擋下（403）。

**XSS**

1. 前端一律用 Vue `{{ }}` 輸出（自動 HTML 跳脫），**全專案不使用 `v-html`**。
2. 後端 `@NoHtml` 驗證拒絕含 `<`、`>` 的文字輸入。
3. 靜態頁（Nginx）：`Content-Security-Policy`（`script-src 'self'`，禁止 inline script）、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy`。
4. API（Spring Security）：`Content-Security-Policy: default-src 'none'`、`nosniff`、`X-Frame-Options: DENY`、`Cache-Control: no-store`。Swagger UI 頁面使用獨立、較寬鬆的 CSP。
5. 錯誤回應的 `detail` 一律為固定文字，不帶 stack trace、SQL 或使用者輸入。

**流量限制（Nginx）**

- 登入：每個 IP 每分鐘 5 次（可額外排隊 5 次），緩解暴力破解密碼。
- 一般 API：每個 IP 每秒 20 次（可額外排隊 40 次）。
- 超過回 `429` 與 JSON 錯誤訊息。

**操作紀錄**

- 記錄誰（登入者）、何時、對哪筆資料、做了什麼（新增 / 修改 / 刪除）、異動前後內容、來源 IP。
- 來源 IP 由 Nginx 以實際連線位址**覆蓋** `X-Forwarded-For` 後轉給後端，用戶端自行帶入的 `X-Forwarded-For` 不會被採用（防止偽造來源 IP）。
- 與資料異動在同一交易中寫入；應用程式帳號沒有任何能刪改紀錄的 SP，**無法竄改紀錄**。

**其他**

- 每筆資料都以 `user_id + sn` 定位；不接受未定義的 JSON 欄位（防止竄改金額）；整數欄位收到小數直接拒絕。
- 密碼由 `.env` 提供，不寫在程式碼；`.env`、憑證、私鑰不進版控。
- 後端容器以非 root 使用者執行；Nginx 不顯示版本號；`/actuator` 只開放 `health` 且不經 Nginx 對外。

**基本滲透測試結果**

以攻擊者角度對本機部署（經 Nginx）實測：

| 類別 | 測試內容 | 結果 |
|---|---|---|
| 偵察 | `/.env`、`/.git/config`、`/actuator/env`、source map、`TRACE` | 皆無洩漏（回 Vue 首頁或 401 / 405）；`Server` header 不含版本 |
| 越權存取 | 讀取 / 修改 / 刪除他人資料、用自己的網址操作他人的序號 | 403 / 404，他人資料未變動 |
| 路徑繞過 | 大小寫、結尾斜線、`//`、`../`、`%2e%2e`、`%2F`、`;`、`%00`、`X-HTTP-Method-Override` | 403 / 400 / 405 |
| SQL Injection | 名稱、帳號、序號帶 SQL 語法 | 400，或被原樣存成文字，資料表完好 |
| 竄改 / 異常輸入 | 多傳 `userId` / `totalFee`、型別混淆、2 MB body、5000 層巢狀 JSON | 400 / 413 |
| XSS | `<script>`、`<img onerror>`（400）；不含 `<>` 的字串（存入但只以文字顯示，前端無 `v-html` / `innerHTML` / 動態 `href`） | 無法執行 |
| CSRF / CORS | 無 token、偽造 token、他人 token；跨站 Origin | 403；無 `Access-Control-Allow-Origin` |
| Session | Session 固定攻擊、登出後重用舊 Session | 登入後換發新 ID；舊 Session 401 |
| 帳號探測 | 「帳號存在密碼錯」vs「帳號不存在」的回應時間 | 152 ms vs 160 ms，無法區分 |
| 暴力破解 | 連續猜密碼、偽造 `X-Forwarded-For` / `X-Real-IP` 繞過限流 | 第 7 次起 429，偽造 header 無效 |
| 網路暴露 | 從區網連資料庫 / 後端 / 網站 | 皆無法連線（只綁 127.0.0.1） |

演練中發現並已修正：

1. **操作紀錄的來源 IP 可被偽造**（中）：Nginx 原本把真實 IP「附加」在用戶端自帶的 `X-Forwarded-For` 之後，而後端信任內網代理，導致偽造值被記入紀錄 → 改為以實際連線 IP 覆蓋。
2. **網站預設對區網開放**（低）：示範帳號密碼公開於 README，同網路的人可登入 → 預設只綁 `127.0.0.1`，需要時以 `WEB_BIND` 開放。

**已知限制與正式上線前需補強**

| 項目 | 現況 | 正式環境做法 |
|---|---|---|
| 身分來源 | 示範帳號寫在 seed 資料，無註冊、改密碼、忘記密碼功能 | 串接企業 SSO / 身分提供者（OIDC），並加上多因素驗證（MFA） |
| 帳號鎖定 | 以 Nginx 流量限制緩解暴力破解 | 連續失敗 N 次鎖定帳號並通知使用者 |
| HTTPS | 提供範本，預設不啟用 | 使用正式憑證；常由負載平衡器 / API Gateway 終結 TLS |
| 流量限制 | 單一 Nginx、以 IP 計算 | 多台部署時改由 API Gateway / WAF 統一限流 |
| 操作紀錄 | 存在同一資料庫 | 另送集中式日誌 / SIEM，並設定保存期限 |
| 並發修改 | 最後寫入為準 | 加 version 欄位做樂觀鎖 |
| 機敏設定 | `.env` 檔 | 改用 Secret 管理服務（如 Vault） |
| 資料庫結構異動 | `DB/01_schema.sql` 為初始化腳本（會先 DROP 資料表） | 改用 Flyway / Liquibase 管理版本化 migration |

## 11. 專案結構與後端分層

```
├─ .github/workflows/ci.yml  # GitHub Actions：測試、建置、冒煙測試
├─ DB/                       # DDL、SP、DML、應用程式帳號
├─ backend/                  # Spring Boot（Maven Wrapper）
│  └─ src/main/java/com/banksystem/
│     ├─ controller/         # 展示層：REST API、參數驗證、HTTP 狀態碼
│     ├─ dto/                # 展示層：request / response 物件
│     ├─ service/            # 業務層：金額計算、業務規則、Transaction 邊界、登入帳號載入
│     ├─ repository/         # 資料層：呼叫 Stored Procedure
│     ├─ model/              # 資料層物件
│     └─ common/             # 共用層
│        ├─ security/        #   登入、授權、CSRF、安全 header
│        ├─ exception/       #   全域例外處理（ProblemDetail）
│        ├─ validation/      #   @NoHtml、格式規則
│        └─ config/          #   API 文件設定
├─ frontend/                 # Vue 3 + Vite、nginx.conf、nginx.https.conf.example
├─ docker-compose.yml
├─ docker-compose.https.example.yml
└─ .env.example
```

| 層 | 職責 | 不做的事 |
|---|---|---|
| 展示層 | 接收 HTTP、`@Valid` 驗證、DTO 轉換、HTTP 狀態碼、取得登入者 | 不寫業務邏輯、不碰 DB |
| 業務層 | 金額計算、存在性檢查、`@Transactional` | 不碰 HTTP、不寫 SQL |
| 資料層 | 呼叫 SP、ResultSet 對應物件 | 不做業務判斷 |
| 共用層 | 安全設定、全域例外處理、驗證規則、API 文件設定 | — |

依賴方向：controller → service → repository，三者皆可使用 common。

## 12. 設計決策

| 項目 | 決定 | 理由 |
|---|---|---|
| 產品與喜好的關係 | 1:1，新增喜好時一併建立產品 | 使用者自行輸入產品名稱 / 價格 / 費率；修改不會影響他人 |
| 使用者識別 | Session 登入；網址的 userId 必須等於登入者 | 補上「改網址即可操作他人資料」的缺口 |
| Session vs JWT | Session（HttpOnly cookie）+ CSRF token | token 不暴露給 JavaScript；登出即失效；單一後端不需無狀態 token |
| 扣款帳號 | 預設帶入使用者帳號，可修改 | 同一使用者可能用不同帳號扣款 |
| 金額進位 | 新台幣整數元、HALF_UP | 扣款以整數元計 |
| 購買數量 | 整數 | — |
| 刪除 | 實體刪除（操作紀錄保留異動前內容） | 資料表保持精簡，稽核需求由操作紀錄滿足 |
| Transaction | Spring `@Transactional`，SP 不含交易語句 | 見第 9 節 |
| 操作紀錄寫入位置 | 在 SP 內與資料異動同一交易 | 不可能出現「有異動沒紀錄」或「有紀錄沒異動」 |
| 金額計算位置 | Java 業務層 | 符合分層、可單元測試 |
| DB 存取 | Spring JDBC `SimpleJdbcCall` | 呼叫 SP 最直接、參數化 |
| HTTPS | 提供範本，預設不啟用 | 本機自簽憑證會造成瀏覽器警告；私鑰不可放進版控 |

## 13. 需求對照表

| 需求 | 實作位置 |
|---|---|
| 新增喜好金融商品 | `POST /api/users/{userId}/likes` → [`LikeController`](backend/src/main/java/com/banksystem/controller/LikeController.java) → `LikeService.create` → `sp_like_create`；前端 [`LikeForm.vue`](frontend/src/components/LikeForm.vue) |
| 查詢喜好清單（產品名稱、扣款帳號、總金額、總手續費、Email） | `GET /api/users/{userId}/likes` → `sp_like_list_by_user`；前端 [`LikeTable.vue`](frontend/src/components/LikeTable.vue)、[`App.vue`](frontend/src/App.vue) |
| 刪除喜好金融商品 | `DELETE /api/users/{userId}/likes/{sn}` → `sp_like_delete` |
| 修改喜好金融商品 | `PUT /api/users/{userId}/likes/{sn}` → `sp_like_update` |
| Web Server + Application Server + RDBMS 三層式架構 | [`docker-compose.yml`](docker-compose.yml)：Nginx → Spring Boot → MySQL |
| 後端分展示層、業務層、資料層、共用層 | 第 11 節 |
| Vue.js 前端 | [`frontend/`](frontend/) |
| Spring Boot | [`backend/`](backend/) |
| RESTful API | 第 6 節、Swagger UI |
| Maven / Gradle | Maven Wrapper（[`backend/mvnw`](backend/mvnw)） |
| 透過 Stored Procedure 存取資料庫 | [`DB/02_procedures.sql`](DB/02_procedures.sql)、[`repository/`](backend/src/main/java/com/banksystem/repository/) |
| 多表異動實作 Transaction | [`LikeService`](backend/src/main/java/com/banksystem/service/LikeService.java) `@Transactional`；第 9 節 |
| DDL / DML 放在 DB 資料夾 | [`DB/`](DB/) |
| 防 SQL Injection、XSS | 第 10 節 |
| （加強）登入與授權、CSRF、流量限制、操作紀錄、HTTPS 範本、健康檢查、API 文件、CI | 第 4、5、10 節 |
