-- =============================================================
-- 02_procedures.sql  (DDL)
-- 所有資料存取都透過以下 Stored Procedure。
--
-- 安全設計：
--   * 只使用參數，不使用 PREPARE / CONCAT 等動態 SQL → 防 SQL Injection。
--   * SQL SECURITY DEFINER：應用程式帳號只有 EXECUTE 權限（見 04_app_user.sh），
--     無法直接讀寫資料表。
--   * 與使用者相關的資料一律同時以 user_id + sn 篩選，避免存取他人資料。
--
-- Transaction：
--   create / update / delete 會同時異動 product 與 like_list，
--   交易由 Java 業務層 @Transactional 控制（commit / rollback）。
--   SP 內刻意「不」寫 START TRANSACTION / COMMIT：MySQL 在交易中再執行
--   START TRANSACTION 會隱含 commit 外層交易，造成無法整筆 rollback。
-- =============================================================

USE banksystem;

DROP PROCEDURE IF EXISTS sp_user_list;
DROP PROCEDURE IF EXISTS sp_user_get;
DROP PROCEDURE IF EXISTS sp_like_list_by_user;
DROP PROCEDURE IF EXISTS sp_like_get;
DROP PROCEDURE IF EXISTS sp_like_create;
DROP PROCEDURE IF EXISTS sp_like_update;
DROP PROCEDURE IF EXISTS sp_like_delete;

DELIMITER $$

-- 查詢所有使用者
CREATE PROCEDURE sp_user_list()
    READS SQL DATA
    SQL SECURITY DEFINER
BEGIN
    SELECT user_id, user_name, email, account
      FROM users
     ORDER BY user_id;
END$$

-- 查詢單一使用者
CREATE PROCEDURE sp_user_get(IN p_user_id VARCHAR(20))
    READS SQL DATA
    SQL SECURITY DEFINER
BEGIN
    SELECT user_id, user_name, email, account
      FROM users
     WHERE user_id = p_user_id;
END$$

-- 查詢某使用者的喜好清單
CREATE PROCEDURE sp_like_list_by_user(IN p_user_id VARCHAR(20))
    READS SQL DATA
    SQL SECURITY DEFINER
BEGIN
    SELECT l.sn, l.product_no, p.product_name, p.price, p.fee_rate,
           l.purchase_quantity, l.account, l.total_fee, l.total_amount, u.email
      FROM like_list l
      JOIN product p ON p.no = l.product_no
      JOIN users   u ON u.user_id = l.user_id
     WHERE l.user_id = p_user_id
     ORDER BY l.sn DESC;
END$$

-- 查詢單筆喜好
CREATE PROCEDURE sp_like_get(IN p_user_id VARCHAR(20), IN p_sn BIGINT)
    READS SQL DATA
    SQL SECURITY DEFINER
BEGIN
    SELECT l.sn, l.product_no, p.product_name, p.price, p.fee_rate,
           l.purchase_quantity, l.account, l.total_fee, l.total_amount, u.email
      FROM like_list l
      JOIN product p ON p.no = l.product_no
      JOIN users   u ON u.user_id = l.user_id
     WHERE l.user_id = p_user_id
       AND l.sn = p_sn;
END$$

-- 新增喜好：寫入 product → like_list（兩張表）
CREATE PROCEDURE sp_like_create(
    IN  p_user_id           VARCHAR(20),
    IN  p_product_name      VARCHAR(100),
    IN  p_price             DECIMAL(15,2),
    IN  p_fee_rate          DECIMAL(5,4),
    IN  p_purchase_quantity INT,
    IN  p_account           VARCHAR(20),
    IN  p_total_fee         DECIMAL(18,2),
    IN  p_total_amount      DECIMAL(18,2),
    OUT p_sn                BIGINT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
BEGIN
    DECLARE v_product_no BIGINT;

    INSERT INTO product (product_name, price, fee_rate)
    VALUES (p_product_name, p_price, p_fee_rate);

    SET v_product_no = LAST_INSERT_ID();

    INSERT INTO like_list (user_id, product_no, purchase_quantity, account, total_fee, total_amount)
    VALUES (p_user_id, v_product_no, p_purchase_quantity, p_account, p_total_fee, p_total_amount);

    SET p_sn = LAST_INSERT_ID();
END$$

-- 修改喜好：更新 product + like_list（兩張表）
-- p_affected = 0 表示找不到（或不屬於該使用者）
CREATE PROCEDURE sp_like_update(
    IN  p_user_id           VARCHAR(20),
    IN  p_sn                BIGINT,
    IN  p_product_name      VARCHAR(100),
    IN  p_price             DECIMAL(15,2),
    IN  p_fee_rate          DECIMAL(5,4),
    IN  p_purchase_quantity INT,
    IN  p_account           VARCHAR(20),
    IN  p_total_fee         DECIMAL(18,2),
    IN  p_total_amount      DECIMAL(18,2),
    OUT p_affected          INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
BEGIN
    DECLARE v_product_no BIGINT DEFAULT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_product_no = NULL;

    SELECT product_no INTO v_product_no
      FROM like_list
     WHERE sn = p_sn AND user_id = p_user_id
       FOR UPDATE;

    IF v_product_no IS NULL THEN
        SET p_affected = 0;
    ELSE
        UPDATE product
           SET product_name = p_product_name,
               price        = p_price,
               fee_rate     = p_fee_rate
         WHERE no = v_product_no;

        UPDATE like_list
           SET purchase_quantity = p_purchase_quantity,
               account           = p_account,
               total_fee         = p_total_fee,
               total_amount      = p_total_amount
         WHERE sn = p_sn;

        SET p_affected = 1;
    END IF;
END$$

-- 刪除喜好：刪除 like_list → product（兩張表）
-- p_affected = 0 表示找不到（或不屬於該使用者）
CREATE PROCEDURE sp_like_delete(
    IN  p_user_id  VARCHAR(20),
    IN  p_sn       BIGINT,
    OUT p_affected INT
)
    MODIFIES SQL DATA
    SQL SECURITY DEFINER
BEGIN
    DECLARE v_product_no BIGINT DEFAULT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_product_no = NULL;

    SELECT product_no INTO v_product_no
      FROM like_list
     WHERE sn = p_sn AND user_id = p_user_id
       FOR UPDATE;

    IF v_product_no IS NULL THEN
        SET p_affected = 0;
    ELSE
        DELETE FROM like_list WHERE sn = p_sn;
        DELETE FROM product   WHERE no = v_product_no;
        SET p_affected = 1;
    END IF;
END$$

DELIMITER ;
