package com.supermarket.pos.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document at /v3/api-docs, UI at /swagger-ui.html (enabled by API_DOCS_ENABLED). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI posOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Supermarket POS API")
                        .version("v1")
                        .description("""
                                Money and quantities are strings ("1250.00"). Authentication uses the \
                                POS_AT HttpOnly cookie set by POST /api/v1/auth/login; state-changing \
                                requests need the X-XSRF-TOKEN header (value of the XSRF-TOKEN cookie). \
                                Errors are RFC 9457 problem details with a stable "code"."""))
                .components(new Components().addSecuritySchemes("cookieAuth",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("POS_AT")))
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
    }
}
