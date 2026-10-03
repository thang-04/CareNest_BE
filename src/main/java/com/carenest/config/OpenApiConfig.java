package com.carenest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Chưa khai báo security scheme: team chưa chốt phương án auth
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI careNestOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CareNest API")
                .version("v1")
                .description("REST API dùng chung cho CareNest web và mobile"));
    }
}
