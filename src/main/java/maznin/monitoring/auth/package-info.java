/**
 * Выпуск JWT-токенов по учётным данным пользователя.
 *
 * <h2>Назначение</h2>
 * Единственная публичная точка входа, доступная без аутентификации:
 * {@code POST /api/v1/auth/token}. Принимает логин и пароль, проверяет их
 * против таблицы {@code users} и возвращает подписанный JWT, который клиент
 * затем передаёт в заголовке {@code Authorization: Bearer} (RFC 6750).
 *
 * <h2>Состав</h2>
 * <ul>
 *   <li>{@link maznin.monitoring.auth.AuthController} — REST-эндпоинт выпуска токена;</li>
 *   <li>{@link maznin.monitoring.auth.AuthService} — проверка учётных данных
 *       (BCrypt) и делегирование генерации токена;</li>
 *   <li>{@link maznin.monitoring.auth.AuthRequest} — DTO запроса: логин и пароль;</li>
 *   <li>{@link maznin.monitoring.auth.AuthResponse} — DTO ответа: JWT-токен.</li>
 * </ul>
 *
 * <h2>Особенности</h2>
 * <ul>
 *   <li>неудачная аутентификация всегда отвечает единообразным 401
 *       ({@code Invalid credentials}) — без раскрытия, что именно неверно:
 *       логин или пароль;</li>
 *   <li>проверка пароля и поиск пользователя выполняются в реактивной
 *       цепочке: {@code findByUsername → filter(matches) → map(token)},
 *       пустой результат на любом шаге даёт 401.</li>
 * </ul>
 *
 * @see maznin.monitoring.security
 */
package maznin.monitoring.auth;
