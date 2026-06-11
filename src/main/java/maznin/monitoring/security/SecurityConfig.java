package maznin.monitoring.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Сборка цепочки фильтров WebFlux Security.
 *
 * <p>Правила: {@code /api/v1/auth/**} и Swagger — публичные, всё остальное
 * требует аутентификации. CSRF, form-login и HTTP Basic отключены —
 * API stateless, единственная схема — Bearer JWT. Ошибки 401/403 отдаются
 * в формате RFC 7807 ({@code application/problem+json}) — формируются здесь,
 * потому что возникают до контроллеров и {@code @RestControllerAdvice}
 * их не видит.</p>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public SecurityConfig(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Цепочка фильтров: правила доступа, JWT-аутентификация через
     * {@link AuthenticationManager} и {@link SecurityContextRepository},
     * обработчики 401/403 с телом RFC 7807.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .exceptionHandling(spec -> spec
                        .authenticationEntryPoint((exchange, e) ->
                                writeProblemDetail(exchange, HttpStatus.UNAUTHORIZED, "Full authentication is required"))
                        .accessDeniedHandler((exchange, e) ->
                                writeProblemDetail(exchange, HttpStatus.FORBIDDEN, "Access is denied"))
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .authorizeExchange(spec -> spec
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/webjars/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }

    /**
     * Пишет в ответ JSON-тело RFC 7807 Problem Details с заданным статусом —
     * используется обработчиками 401 (authenticationEntryPoint) и 403
     * (accessDeniedHandler).
     */
    private Mono<Void> writeProblemDetail(org.springframework.web.server.ServerWebExchange exchange,
                                          HttpStatus status, String detail) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = String.format(
                "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"instance\":\"%s\"}",
                status.getReasonPhrase(), status.value(), detail,
                exchange.getRequest().getPath().value()
        );
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * CORS для SPA: разрешены источники локальной разработки и
     * nginx-прокси, заголовки {@code Content-Type} и {@code Authorization}.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost", "http://127.0.0.1"));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    /** BCrypt — формат хэшей в колонке {@code users.password_hash}. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
