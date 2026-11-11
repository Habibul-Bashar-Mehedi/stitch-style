package com.stitchstyle.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI stitchStyleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Stitch & Style API")
                .version("v1")
                .description("Dynamic e-commerce API for the Bangladesh market."));
    }
}
