/**
 * Учётные записи операторов системы.
 *
 * <h2>Назначение</h2>
 * Хранение пользователей, которым разрешён вход в панель наблюдения.
 * Пароли хранятся только в виде BCrypt-хэшей; стартовый пользователь
 * ({@code admin}) создаётся скриптом {@code schema.sql}.
 *
 * <h2>Состав</h2>
 * <ul>
 *   <li>{@link maznin.monitoring.user.User} — сущность таблицы {@code users},
 *       реализует {@code UserDetails} Spring Security (фиксированная роль
 *       {@code ROLE_USER}, без блокировок и сроков действия);</li>
 *   <li>{@link maznin.monitoring.user.UserRepository} — реактивный
 *       репозиторий с поиском по логину.</li>
 * </ul>
 */
package maznin.monitoring.user;
