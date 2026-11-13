package com.banksystem.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.banksystem.model.LikeAmount;
import com.banksystem.model.LikeInput;
import com.banksystem.model.LikeItem;
import com.banksystem.service.FeeCalculator;
import com.banksystem.service.LikeService;

/**
 * 整合測試：以 Testcontainers 啟動真正的 MySQL，並執行專案 DB/ 下的 DDL、SP、DML、帳號腳本。
 * 驗證：
 *  1. 新增 / 修改 / 刪除經 Stored Procedure 正確運作
 *  2. 第二張表寫入失敗時，整筆交易 rollback（不留下半套資料）
 *  3. 應用程式帳號無法直接存取資料表（只能呼叫 SP）
 *
 * 沒有 Docker 的環境會自動略過。
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LikeTransactionIntegrationTest {

    private static final String APP_USER = "banksystem_app";
    private static final String APP_PASSWORD = "it_app_password";
    private static final String USER_ID = "A1236456789";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("banksystem")
            .withEnv("APP_DB_USER", APP_USER)
            .withEnv("APP_DB_PASSWORD", APP_PASSWORD)
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci")
            .withCopyFileToContainer(dbScript("01_schema.sql"), "/docker-entrypoint-initdb.d/01_schema.sql")
            .withCopyFileToContainer(dbScript("02_procedures.sql"), "/docker-entrypoint-initdb.d/02_procedures.sql")
            .withCopyFileToContainer(dbScript("03_seed.sql"), "/docker-entrypoint-initdb.d/03_seed.sql")
            .withCopyFileToContainer(dbScript("04_app_user.sh"), "/docker-entrypoint-initdb.d/04_app_user.sh");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
    }

    @Autowired
    private LikeService likeService;

    /** 應用程式使用的連線（只有 EXECUTE 權限）。 */
    @Autowired
    private JdbcTemplate appJdbcTemplate;

    @MockitoSpyBean
    private FeeCalculator feeCalculator;

    @Test
    void createUpdateDeleteThroughStoredProcedures() {
        int productsBefore = count("product");

        LikeItem created = likeService.create(USER_ID, input("整合測試基金", "12.34", "0.015", 7));
        assertThat(created.totalFee()).isEqualByComparingTo("1");
        assertThat(created.totalAmount()).isEqualByComparingTo("87");
        assertThat(created.email()).isEqualTo("test@email.com");

        LikeItem updated = likeService.update(USER_ID, created.sn(), input("整合測試基金B", "100", "0.01", 2));
        assertThat(updated.productName()).isEqualTo("整合測試基金B");
        assertThat(updated.totalAmount()).isEqualByComparingTo("202");

        likeService.delete(USER_ID, created.sn());
        assertThat(count("product")).isEqualTo(productsBefore);
        assertThat(count("like_list WHERE sn = " + created.sn())).isZero();
    }

    @Test
    void createRollsBackProductWhenLikeListInsertFails() {
        int productsBefore = count("product");
        int likesBefore = count("like_list");
        // 讓 like_list 違反 CHECK (total_fee >= 0)：product 已寫入後，like_list 才失敗
        doReturn(new LikeAmount(new BigDecimal("-1"), BigDecimal.ONE))
                .when(feeCalculator).calculate(any(), anyInt(), any());

        assertThatThrownBy(() -> likeService.create(USER_ID, input("會失敗的商品", "100", "0.01", 1)))
                .isInstanceOf(DataAccessException.class);

        assertThat(count("product")).as("product 必須一起 rollback").isEqualTo(productsBefore);
        assertThat(count("like_list")).isEqualTo(likesBefore);
    }

    @Test
    void updateRollsBackProductWhenLikeListUpdateFails() {
        String nameBefore = root().queryForObject("SELECT product_name FROM product WHERE no = 1", String.class);
        doReturn(new LikeAmount(new BigDecimal("-1"), BigDecimal.ONE))
                .when(feeCalculator).calculate(any(), anyInt(), any());

        assertThatThrownBy(() -> likeService.update(USER_ID, 1L, input("不該被寫入的名稱", "1", "0", 1)))
                .isInstanceOf(DataAccessException.class);

        String nameAfter = root().queryForObject("SELECT product_name FROM product WHERE no = 1", String.class);
        assertThat(nameAfter).as("product 的修改必須一起 rollback").isEqualTo(nameBefore);
    }

    @Test
    void appAccountCannotAccessTablesDirectly() {
        assertThatThrownBy(() -> appJdbcTemplate.queryForList("SELECT * FROM users"))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("SELECT command denied");
    }

    private static LikeInput input(String name, String price, String feeRate, int qty) {
        return new LikeInput(name, new BigDecimal(price), new BigDecimal(feeRate), qty, "1111999666");
    }

    private static MountableFile dbScript(String fileName) {
        return MountableFile.forHostPath("../DB/" + fileName);
    }

    /** 以 root 連線查資料，僅供測試驗證用。 */
    private static JdbcTemplate root() {
        return new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
    }

    private static int count(String tableAndWhere) {
        Integer n = root().queryForObject("SELECT COUNT(*) FROM " + tableAndWhere, Integer.class);
        return n == null ? 0 : n;
    }
}
