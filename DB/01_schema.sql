-- =============================================================
-- 01_schema.sql  (DDL)
-- 金融商品喜好紀錄系統：建立資料庫與資料表
--
-- 【注意】本腳本用於「空白資料庫初始化」，會先 DROP 既有資料表，
--         重複執行將清除所有資料（含操作紀錄）。
--         正式環境的結構異動應改用 migration 工具（如 Flyway / Liquibase）。
-- =============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS banksystem
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE banksystem;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS like_list;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS users;

-- 使用者
CREATE TABLE users (
    user_id    VARCHAR(20)  NOT NULL COMMENT '使用者 ID',
    user_name  VARCHAR(50)  NOT NULL COMMENT '使用者名稱',
    email      VARCHAR(100) NOT NULL COMMENT '使用者電子郵件',
    account    VARCHAR(20)  NOT NULL COMMENT '預設扣款帳號',
    password_hash VARCHAR(100) NOT NULL COMMENT '登入密碼（BCrypt 雜湊，不存明文）',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (user_id)
) ENGINE = InnoDB COMMENT = '使用者';

-- 產品（每筆喜好對應一筆產品，1:1）
CREATE TABLE product (
    no           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '產品流水號',
    product_name VARCHAR(100)  NOT NULL COMMENT '產品名稱',
    price        DECIMAL(15,2) NOT NULL COMMENT '產品價格',
    fee_rate     DECIMAL(5,4)  NOT NULL COMMENT '手續費率，0.01 = 1%',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_product PRIMARY KEY (no),
    CONSTRAINT ck_product_price    CHECK (price > 0),
    CONSTRAINT ck_product_fee_rate CHECK (fee_rate >= 0 AND fee_rate <= 1)
) ENGINE = InnoDB COMMENT = '產品資料';

-- 喜好清單
CREATE TABLE like_list (
    sn                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '流水序號',
    user_id           VARCHAR(20)   NOT NULL COMMENT '使用者 ID',
    product_no        BIGINT        NOT NULL COMMENT '產品流水號',
    purchase_quantity INT           NOT NULL COMMENT '購買數量',
    account           VARCHAR(20)   NOT NULL COMMENT '扣款帳號',
    total_fee         DECIMAL(18,2) NOT NULL COMMENT '總手續費用（新台幣）',
    total_amount      DECIMAL(18,2) NOT NULL COMMENT '預計扣款總金額（新台幣）',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_like_list PRIMARY KEY (sn),
    CONSTRAINT uk_like_list_product_no UNIQUE (product_no),
    CONSTRAINT fk_like_list_user FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_like_list_product FOREIGN KEY (product_no) REFERENCES product (no)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_like_list_quantity     CHECK (purchase_quantity > 0),
    CONSTRAINT ck_like_list_total_fee    CHECK (total_fee >= 0),
    CONSTRAINT ck_like_list_total_amount CHECK (total_amount >= 0)
) ENGINE = InnoDB COMMENT = '喜好清單';

CREATE INDEX idx_like_list_user_id ON like_list (user_id);

-- 操作紀錄：誰、何時、對哪筆資料做了什麼（異動前後內容）
-- 由 create / update / delete SP 在同一交易中寫入；應用程式帳號沒有刪改紀錄的 SP，無法竄改
CREATE TABLE audit_log (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '紀錄序號',
    actor_user_id VARCHAR(20) NOT NULL COMMENT '執行操作的使用者（登入者）',
    owner_user_id VARCHAR(20) NOT NULL COMMENT '被異動資料的擁有者',
    action        VARCHAR(10) NOT NULL COMMENT 'CREATE / UPDATE / DELETE',
    like_sn       BIGINT      NOT NULL COMMENT '喜好清單序號（資料刪除後紀錄仍保留，故不設 FK）',
    before_data   JSON        NULL     COMMENT '異動前內容（新增時為 NULL）',
    after_data    JSON        NULL     COMMENT '異動後內容（刪除時為 NULL）',
    client_ip     VARCHAR(45) NULL     COMMENT '來源 IP（IPv4 / IPv6）',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_audit_log_owner FOREIGN KEY (owner_user_id) REFERENCES users (user_id),
    CONSTRAINT ck_audit_log_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
) ENGINE = InnoDB COMMENT = '操作紀錄';

CREATE INDEX idx_audit_log_owner ON audit_log (owner_user_id, id);
