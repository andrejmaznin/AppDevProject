/**
 * Проверка JWT-токенов в цепочке фильтров WebFlux Security.
 *
 * <h2>Назначение</h2>
 * Защищает все эндпоинты, кроме {@code /api/v1/auth/**} и Swagger.
 * Каждый запрос проходит цепочку: извлечение Bearer-токена из заголовка →
 * криптографическая проверка подписи и срока действия → построение
 * {@code Authentication} с ролью {@code ROLE_USER}.
 *
 * <h2>Состав (паттерн: Стратегия — реализации точек расширения фреймворка)</h2>
 * <ul>
 *   <li>{@link maznin.monitoring.security.SecurityConfig} — сборка
 *       {@code SecurityWebFilterChain}: правила доступа, CORS, отключение
 *       CSRF/Basic/FormLogin, обработчики 401/403 в формате RFC 7807;</li>
 *   <li>{@link maznin.monitoring.security.SecurityContextRepository} —
 *       реализация {@code ServerSecurityContextRepository}: достаёт токен из
 *       {@code Authorization: Bearer ...};</li>
 *   <li>{@link maznin.monitoring.security.AuthenticationManager} —
 *       реализация {@code ReactiveAuthenticationManager}: валидирует токен
 *       через {@link maznin.monitoring.security.JwtService};</li>
 *   <li>{@link maznin.monitoring.security.JwtService} — генерация и разбор
 *       JWT (HS256, срок жизни 24 ч по умолчанию).</li>
 * </ul>
 *
 * <h2>Особенности</h2>
 * <ul>
 *   <li>любая проблема с токеном (повреждён, просрочен, чужая подпись)
 *       приводит к {@code Mono.empty()} от менеджера аутентификации — клиент
 *       получает аккуратный 401, а не 500;</li>
 *   <li>сессии на сервере не хранятся: состояние аутентификации целиком в
 *       токене (stateless), метод {@code save} контекста не поддерживается;</li>
 *   <li>секрет и срок жизни токена настраиваются свойствами
 *       {@code application.security.jwt.secret-key} и
 *       {@code application.security.jwt.expiration}.</li>
 * </ul>
 */
package maznin.monitoring.security;
