/**
 * Централизованная обработка ошибок в формате Problem Details
 * (RFC 9457, ранее RFC 7807) с каталогом типов проблем.
 *
 * <h2>Назначение</h2>
 * Гарантирует, что любой неуспешный ответ API имеет единообразное JSON-тело
 * c полями {@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance} и media type {@code application/problem+json}. Известным
 * классам ошибок присваиваются уникальные стабильные URI в поле {@code type}
 * — клиент различает «пациент не найден» и «неверный пароль» машинно,
 * а не по тексту.
 *
 * <h2>Состав</h2>
 * <ul>
 *   <li>{@link maznin.monitoring.error.ProblemType} — каталог типов проблем:
 *       URI, статус, название, описание и действия по устранению; единственный
 *       источник истины для ответов об ошибках и базы знаний;</li>
 *   <li>{@link maznin.monitoring.error.GlobalExceptionHandler} —
 *       {@code @RestControllerAdvice}: преобразует
 *       {@code ResponseStatusException} (ожидаемые ошибки: 404 и т.п.) и любые
 *       прочие исключения (500, с логированием стектрейса) в Problem Details
 *       с типом из каталога;</li>
 *   <li>{@link maznin.monitoring.error.ProblemCatalogController} —
 *       {@code GET /api/v1/problems}: машиночитаемый справочник всех типов,
 *       источник данных раздела «База знаний» во фронтенде.</li>
 * </ul>
 *
 * <h2>Особенности</h2>
 * <ul>
 *   <li>ошибки авторизации (401/403) возникают до контроллеров — в цепочке
 *       фильтров безопасности, поэтому формируются отдельно в
 *       {@link maznin.monitoring.security.SecurityConfig}, но из того же
 *       каталога; у двух разных 401 разные типы:
 *       {@code invalid-credentials} (неверный пароль при выпуске токена)
 *       и {@code authentication-required} (нет/невалидный токен);</li>
 *   <li>реестр IANA из RFC 9457 предназначен для типов, переиспользуемых
 *       между организациями; для типов уровня приложения стандарт предписывает
 *       собственные стабильные URI — что здесь и реализовано.</li>
 * </ul>
 */
package maznin.monitoring.error;
