package maznin.monitoring.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Извлечение контекста безопасности из заголовка запроса (реализация точки
 * расширения {@code ServerSecurityContextRepository}).
 *
 * <p>Stateless-схема: контекст не сохраняется между запросами, а каждый раз
 * строится заново из {@code Authorization: Bearer <jwt>}.</p>
 */
@Component
public class SecurityContextRepository implements ServerSecurityContextRepository {

    private final AuthenticationManager authenticationManager;

    public SecurityContextRepository(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * Не поддерживается: состояние аутентификации живёт только в токене,
     * серверного хранения сессий нет.
     */
    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Достаёт Bearer-токен из заголовка {@code Authorization} и делегирует
     * проверку {@link AuthenticationManager}. Отсутствие заголовка или иная
     * схема → пустой {@code Mono} → анонимный запрос (401 на защищённых
     * путях).
     *
     * @param exchange текущий HTTP-обмен
     * @return контекст с аутентификацией или пустой {@code Mono}
     */
    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(authHeader -> authHeader.startsWith("Bearer "))
                .flatMap(authHeader -> {
                    String authToken = authHeader.substring(7);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(authToken, authToken);
                    return this.authenticationManager.authenticate(auth).map(SecurityContextImpl::new);
                });
    }
}
