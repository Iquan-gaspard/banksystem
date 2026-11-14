package com.banksystem.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;
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

import com.banksystem.model.Actor;
import com.banksystem.model.AuditLog;
import com.banksystem.model.LikeAmount;
import com.banksystem.model.LikeInput;
import com.banksystem.model.LikeItem;
import com.banksystem.service.AuditLogService;
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
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LikeTransactionIntegrationTest {

    private static final String APP_USER = "banksystem_app";
    private static final String APP_PASSWORD = "it_app_password";
    private static final String USER_ID = "A1236456789";
    private static final Actor ACTOR = new Actor(USER_ID, "203.0.113.10");

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

    @Autowired
    private AuditLogService auditLogService;

    /** 應用程式使用的連線（只有 EXECUTE 權限）。 */
    @Autowired
    private JdbcTemplate appJdbcTemplate;

    @MockitoSpyBean
    private FeeCalculator feeCalculator;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWithSeedAccountAndAccessOnlyOwnData() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("userId", USER_ID).param("password", "Demo@1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andReturn().getRequest().getSession();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@email.com"));
        mockMvc.perform(get("/api/users/" + USER_ID + "/likes").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/B2345678901/likes").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("userId", USER_ID).param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("帳號或密碼錯誤"));
    }

    @Test
    void createUpdateDeleteThroughStoredProcedures() {
        int productsBefore = count("product");

        LikeItem created = likeService.create(USER_ID, input("整合測試基金", "12.34", "0.015", 7), ACTOR);
        assertThat(created.totalFee()).isEqualByComparingTo("1");
        assertThat(created.totalAmount()).isEqualByComparingTo("87");
        assertThat(created.email()).isEqualTo("test@email.com");

        LikeItem updated = likeService.update(USER_ID, created.sn(), input("整合測試基金B", "100", "0.01", 2), ACTOR);
        assertThat(updated.productName()).isEqualTo("整合測試基金B");
        assertThat(updated.totalAmount()).isEqualByComparingTo("202");

        likeService.delete(USER_ID, created.sn(), ACTOR);
        assertThat(count("product")).isEqualTo(productsBefore);
        assertThat(count("like_list WHERE sn = " + created.sn())).isZero();

        // 操作紀錄：新增 → 修改 → 刪除，記錄執行者、IP 與異動前後內容（資料刪除後紀錄仍保留）
        List<AuditLog> logs = auditLogService.getAuditLogs(USER_ID).stream()
                .filter(log -> log.likeSn() == created.sn())
                .toList();
        assertThat(logs).extracting(AuditLog::action).containsExactly("DELETE", "UPDATE", "CREATE");
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.actorUserId()).isEqualTo(USER_ID);
            assertThat(log.clientIp()).isEqualTo(ACTOR.clientIp());
        });
        AuditLog update = logs.get(1);
        assertThat(update.beforeData()).contains("\"productName\": \"整合測試基金\"");
        assertThat(update.afterData()).contains("\"productName\": \"整合測試基金B\"");
        // 快照中的金額為字串，前端讀取時不會失去精度
        assertThat(update.afterData()).contains("\"totalAmount\": \"202.00\"", "\"price\": \"100.00\"");
        assertThat(logs.get(0).afterData()).isNull();
        assertThat(logs.get(2).beforeData()).isNull();
    }

    /** 完整 HTTP 流程：登入 → 新增 → 查詢 → 修改 → 刪除 → 操作紀錄，經過 Spring Security、Controller、SP 與真實 MySQL。 */
    @Test
    void crudThroughHttpApiWithRealLogin() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("userId", USER_ID).param("password", "Demo@1234"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();
        String base = "/api/users/" + USER_ID + "/likes";

        String created = mockMvc.perform(post(base).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"HTTP測試基金","price":"1000","feeRate":"0.01","purchaseQuantity":3,"account":"1111999666"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalFee").value("30.00"))
                .andExpect(jsonPath("$.totalAmount").value("3030.00"))
                .andReturn().getResponse().getContentAsString();
        long sn = ((Number) JsonPath.read(created, "$.sn")).longValue();

        mockMvc.perform(get(base + "/" + sn).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("HTTP測試基金"));

        mockMvc.perform(put(base + "/" + sn).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"HTTP測試基金","price":"12.34","feeRate":"0.015","purchaseQuantity":7,"account":"1111999666"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFee").value("1.00"))
                .andExpect(jsonPath("$.totalAmount").value("87.00"));

        // 他人無法修改（授權層擋下）
        mockMvc.perform(put("/api/users/B2345678901/likes/" + sn).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"x","price":"1","feeRate":"0","purchaseQuantity":1,"account":"1111999666"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(base + "/" + sn).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(base + "/" + sn).session(session))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/" + USER_ID + "/audit-logs").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("DELETE"))
                .andExpect(jsonPath("$[0].likeSn").value(sn))
                .andExpect(jsonPath("$[1].action").value("UPDATE"))
                .andExpect(jsonPath("$[1].after.totalAmount").value("87.00"))
                .andExpect(jsonPath("$[2].action").value("CREATE"));
    }

    @Test
    void createRollsBackProductWhenLikeListInsertFails() {
        int productsBefore = count("product");
        int likesBefore = count("like_list");
        int auditsBefore = count("audit_log");
        // 讓 like_list 違反 CHECK (total_fee >= 0)：product 已寫入後，like_list 才失敗
        doReturn(new LikeAmount(new BigDecimal("-1"), BigDecimal.ONE))
                .when(feeCalculator).calculate(any(), anyInt(), any());

        assertThatThrownBy(() -> likeService.create(USER_ID, input("會失敗的商品", "100", "0.01", 1), ACTOR))
                .isInstanceOf(DataAccessException.class);

        assertThat(count("product")).as("product 必須一起 rollback").isEqualTo(productsBefore);
        assertThat(count("like_list")).isEqualTo(likesBefore);
        assertThat(count("audit_log")).as("失敗的操作不留紀錄").isEqualTo(auditsBefore);
    }

    @Test
    void updateRollsBackProductWhenLikeListUpdateFails() {
        String nameBefore = root().queryForObject("SELECT product_name FROM product WHERE no = 1", String.class);
        doReturn(new LikeAmount(new BigDecimal("-1"), BigDecimal.ONE))
                .when(feeCalculator).calculate(any(), anyInt(), any());

        assertThatThrownBy(() -> likeService.update(USER_ID, 1L, input("不該被寫入的名稱", "1", "0", 1), ACTOR))
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
