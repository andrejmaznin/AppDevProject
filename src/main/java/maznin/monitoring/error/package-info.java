/**
 * Централизованная обработка ошибок в формате RFC 7807 Problem Details.
 *
 * <h2>Назначение</h2>
 * Гарантирует, что любой неуспешный ответ API имеет единообразное JSON-тело
 * c полями {@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance} и media type {@code application/problem+json}.
 *
 * <h2>Состав</h2>
 * <ul>
 *   <li>{@link maznin.monitoring.error.GlobalExceptionHandler} —
 *       {@code @RestControllerAdvice}: преобразует
 *       {@code ResponseStatusException} (ожидаемые ошибки: 404 и т.п.) и любые
 *       прочие исключения (500, с логированием стектрейса) в Problem Details.</li>
 * </ul>
 *
 * <h2>Особенности</h2>
 * Ошибки авторизации (401/403) возникают до контроллеров — в цепочке фильтров
 * безопасности, поэтому формируются отдельно в
 * {@link maznin.monitoring.security.SecurityConfig}, но в том же формате.
 */
package maznin.monitoring.error;
