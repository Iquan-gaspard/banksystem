#!/bin/bash
# =============================================================
# 04_app_user.sh
# 建立應用程式專用的 DB 帳號，只授予 EXECUTE（只能呼叫 Stored Procedure），
# 不能直接 SELECT / INSERT / UPDATE / DELETE 資料表（最小權限原則）。
#
# 由 MySQL 官方 Docker image 在第一次初始化時自動執行，
# 帳號密碼來自環境變數（.env），不寫死在程式碼中。
#
# 不使用 Docker 時，請手動執行等效 SQL：
#   CREATE USER 'banksystem_app'@'%' IDENTIFIED BY '<password>';
#   GRANT EXECUTE ON banksystem.* TO 'banksystem_app'@'%';
# =============================================================

: "${APP_DB_USER:?APP_DB_USER is required}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD is required}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE USER IF NOT EXISTS '${APP_DB_USER}'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}';
GRANT EXECUTE ON banksystem.* TO '${APP_DB_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL
