package com.jc.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    /** Swagger UI에서 Bearer JWT를 직접 입력해 보호 API를 호출할 수 있도록 설정합니다. */
    @Bean
    fun customOpenAPI(): OpenAPI {
        val schemeName = "jwtAuth"
        val securityScheme = SecurityScheme()
            .name(schemeName)
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")

        return OpenAPI()
            .info(
                Info()
                    .title("Journey Connect API")
                    .description("글로벌 여행 정보 공유 플랫폼 Journey Connect 백엔드 API")
                    .version("v1"),
            )
            .addSecurityItem(SecurityRequirement().addList(schemeName))
            .components(Components().addSecuritySchemes(schemeName, securityScheme))
    }
}
