package com.jc.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jc.backend.common.ApiErrorResponse
import com.jc.backend.database.DatabaseRequestIdentityFilter
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Journey Connect REST API의 인증·인가 경계를 한 곳에서 관리합니다.
 *
 * <p>기존 Java 설정과 Kotlin 설정이 동시에 등록되면 같은 이름의 SecurityFilterChain과 CORS Bean이
 * 충돌하므로, Kotlin 설정 하나만 애플리케이션의 보안 진입점으로 사용합니다.
 * 공개 엔드포인트와 인증이 필요한 엔드포인트의 경계를 명확히 분리해 API 접근 정책을 한곳에서 관리합니다.
 */
@Configuration
class SecurityConfig(
    private val objectMapper: ObjectMapper,
    private val databaseRequestIdentityFilter: DatabaseRequestIdentityFilter,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun databaseRequestIdentityFilterRegistration(
        filter: DatabaseRequestIdentityFilter,
    ): FilterRegistrationBean<DatabaseRequestIdentityFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // JWT 기반 API는 서버 세션을 저장하지 않으므로 CSRF 토큰을 사용하지 않습니다.
            .csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/test/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                    ).permitAll()
                    // 공개 와일드카드보다 구체적인 보호 경로를 먼저 선언합니다.
                    .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/crews/*/applications").authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/feed",
                        "/api/v1/feed/page",
                        "/api/v1/explore",
                        "/api/v1/posts/**",
                        "/api/v1/crews/**",
                        "/api/v1/users/*/posts",
                        "/api/v1/regions",
                        "/api/v1/regions/**",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                // Spring Security 단계에서 발생한 401/403도 도메인 오류와 동일한 JSON 구조로 반환합니다.
                exceptions.authenticationEntryPoint { _, response, _ ->
                    writeSecurityError(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        ApiErrorResponse.authenticationRequired(),
                    )
                }
                exceptions.accessDeniedHandler { _, response, _ ->
                    writeSecurityError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        ApiErrorResponse.accessDenied(),
                    )
                }
            }
            .oauth2ResourceServer { oauth ->
                oauth.jwt { }
                oauth.authenticationEntryPoint { _, response, _ ->
                    writeSecurityError(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        ApiErrorResponse.authenticationRequired(),
                    )
                }
            }
            .addFilterAfter(
                databaseRequestIdentityFilter,
                BearerTokenAuthenticationFilter::class.java,
            )

        return http.build()
    }

    @Bean
    fun jwtEncoder(@Value("\${app.security.jwt-secret}") secret: String): JwtEncoder =
        NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey(secret)))

    @Bean
    fun jwtDecoder(@Value("\${app.security.jwt-secret}") secret: String): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(secretKey(secret)).build()

    @Bean
    fun corsConfigurationSource(
        @Value("\${app.cors.allowed-origins}") allowedOrigins: List<String>,
    ): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            this.allowedOrigins = allowedOrigins
            allowedMethods = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "Idempotency-Key",
                "X-Recommendation-Run-Id",
                "X-Recommendation-Event-Id",
                "X-Recommendation-Occurred-At",
            )
            allowCredentials = true
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    private fun secretKey(secret: String): SecretKey {
        val bytes = secret.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size >= 32) {
            "JWT_SECRET은 HS256 사용을 위해 최소 32바이트 이상이어야 합니다."
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    private fun writeSecurityError(
        response: HttpServletResponse,
        status: Int,
        body: ApiErrorResponse,
    ) {
        response.status = status
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, body)
    }
}
