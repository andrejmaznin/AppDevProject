package maznin.monitoring.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Проверка JWT в цепочке WebFlux Security (реализация точки расширения
 * {@code ReactiveAuthenticationManager}).
 *
 * <p>Контракт: валидный токен → {@code Authentication} с логином и ролью
 * {@code ROLE_USER}; любой невалидный (повреждённый, просроченный, с чужой
 * подписью) → {@code Mono.empty()}, что фреймворк превращает в 401.
 * Исключения парсера никогда не пробрасываются — иначе клиент получал бы
 * 500 вместо 401.</p>
 */
@Component
public class AuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;

    public AuthenticationManager(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Аутентифицирует по JWT из {@code credentials}.
     *
     * @param authentication токен, упакованный {@link SecurityContextRepository}
     * @return {@code Authentication} при валидном токене, иначе пустой {@code Mono}
     */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();
        String username;
        try {
            username = jwtService.extractUsername(authToken);
        } catch (Exception e) {
            // Malformed/expired/forged token — treat as unauthenticated, not a server error
            return Mono.empty();
        }

        return Mono.justOrEmpty(username)
                .flatMap(user -> {
                    if (jwtService.isTokenValid(authToken, new org.springframework.security.core.userdetails.User(username, "", List.of(new SimpleGrantedAuthority("ROLE_USER"))))) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        return Mono.just(auth);
                    } else {
                        return Mono.empty();
                    }
                });
    }
}
