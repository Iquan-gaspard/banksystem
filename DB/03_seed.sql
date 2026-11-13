-- =============================================================
-- 03_seed.sql  (DML)
-- 示範資料。金額依計算規則：
--   total_fee    = ROUND_HALF_UP(price × qty × fee_rate, 0)
--   total_amount = ROUND_HALF_UP(price × qty, 0) + total_fee
-- =============================================================

SET NAMES utf8mb4;
USE banksystem;

-- 示範帳號密碼皆為 Demo@1234（以 BCrypt 雜湊儲存，資料庫不存明文；正式環境不可使用）
SET @demo_password_hash = '$2a$10$ygDoypCPbzzBhTvmSi/fVOa/snHfGUgiuqk8OvsKUH1Izel9gRz/u';

INSERT INTO users (user_id, user_name, email, account, password_hash) VALUES
    ('A1236456789', '王o明', 'test@email.com',  '1111999666', @demo_password_hash),
    ('B2345678901', '李o華', 'lee@email.com',   '2222888555', @demo_password_hash),
    ('C3456789012', '陳o美', 'chen@email.com',  '3333777444', @demo_password_hash);

INSERT INTO product (no, product_name, price, fee_rate) VALUES
    (1, '全球債券基金A',     1000.00, 0.0100),
    (2, '台灣高股息ETF',       35.50, 0.0015),
    (3, '美元貨幣市場基金',    10.25, 0.0000);

INSERT INTO like_list (sn, user_id, product_no, purchase_quantity, account, total_fee, total_amount) VALUES
    -- 1000.00 × 3 = 3000；fee = 3000 × 0.01 = 30；total = 3030
    (1, 'A1236456789', 1, 3,    '1111999666', 30, 3030),
    -- 35.50 × 1000 = 35500；fee = 53.25 → 53；total = 35553
    (2, 'A1236456789', 2, 1000, '1111999666', 53, 35553),
    -- 10.25 × 200 = 2050；fee = 0；total = 2050
    (3, 'B2345678901', 3, 200,  '2222888555', 0,  2050);
