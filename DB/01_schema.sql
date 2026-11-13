-- =============================================================
-- 01_schema.sql  (DDL)
-- 金融商品喜好紀錄系統：建立資料庫與資料表
-- =============================================================

CREATE DATABASE IF NOT EXISTS banksystem
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE banksystem;

DROP TABLE IF EXISTS like_list;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS users;

-- 使用者
CREATE TABLE users (
    user_id    VARCHAR(20)  NOT NULL COMMENT '使用者 ID',
    user_name  VARCHAR(50)  NOT NULL COMMENT '使用者名稱',
    email      VARCHAR(100) NOT NULL COMMENT '使用者電子郵件',
    account    VARCHAR(20)  NOT NULL COMMENT '預設扣款帳號',
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
