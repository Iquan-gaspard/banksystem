package com.banksystem.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** API 文件基本資訊（/api/swagger-ui.html）。 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("金融商品喜好紀錄系統 API")
                .version("1.0")
                .description("""
                        需先登入：於網站登入後，同一瀏覽器開啟本頁即可使用 Session 呼叫 API。
                        登入：POST /api/auth/login（表單欄位 userId、password）；
                        登出：POST /api/auth/logout。"""));
    }
}
