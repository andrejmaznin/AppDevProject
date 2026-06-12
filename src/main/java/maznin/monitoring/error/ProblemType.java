package maznin.monitoring.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.net.URI;

/**
 * Каталог типов проблем приложения (RFC 9457 Problem Details).
 *
 * <p>RFC 9457 (преемник RFC 7807) рекомендует для содержательных классов
 * ошибок определять собственные стабильные URI в поле {@code type} вместо
 * {@code about:blank}. Этот enum — единственный источник истины каталога:
 * из него заполняются ответы об ошибках ({@link GlobalExceptionHandler},
 * {@code SecurityConfig}) и отдаётся машиночитаемый справочник
 * {@code GET /api/v1/problems}, на котором построена «База знаний»
 * во фронтенде.</p>
 *
 * <p>URI типов — относительные ссылки вида {@code /problems/<slug>};
 * идентификатор по RFC не обязан быть разыменовываемым. В продакшене
 * это были бы абсолютные URL на страницы документации.</p>
 */
public enum ProblemType {

    INVALID_REQUEST("invalid-request", HttpStatus.BAD_REQUEST,
            "Некорректный запрос",
            "Параметры или тело запроса не прошли разбор: повреждённый UUID в пути, "
                    + "метка времени не в формате RFC 3339, нечитаемый JSON.",
            "Проверьте формат данных: идентификаторы — UUID, параметры from/to — "
                    + "RFC 3339 (например, 2026-06-11T10:00:00Z), тело — корректный JSON "
                    + "с полями из документации API."),

    INVALID_CREDENTIALS("invalid-credentials", HttpStatus.UNAUTHORIZED,
            "Неверные учётные данные",
            "Пара логин/пароль не подошла при выпуске токена (POST /api/v1/auth/token). "
                    + "Система намеренно не уточняет, что именно неверно — логин или пароль.",
            "Проверьте логин и пароль (раскладку, регистр). Стартовая учётная запись "
                    + "из поставки: admin / admin123. Если пароль утерян — обновите "
                    + "BCrypt-хэш в таблице users."),

    AUTHENTICATION_REQUIRED("authentication-required", HttpStatus.UNAUTHORIZED,
            "Требуется аутентификация",
            "Запрос к защищённому ресурсу без токена, с повреждённым, просроченным "
                    + "или подписанным другим ключом JWT.",
            "Войдите заново и повторите запрос с заголовком "
                    + "Authorization: Bearer <token>. Срок жизни токена — 24 часа; "
                    + "интерфейс при этой ошибке автоматически возвращает на экран входа."),

    ACCESS_DENIED("access-denied", HttpStatus.FORBIDDEN,
            "Доступ запрещён",
            "Пользователь аутентифицирован, но его роли недостаточно для операции. "
                    + "В текущей модели прав (единственная роль ROLE_USER) на практике "
                    + "не возникает.",
            "Обратитесь к администратору за расширением прав. Если ошибка возникла "
                    + "при штатной работе — это дефект, приложите детали запроса."),

    PATIENT_NOT_FOUND("patient-not-found", HttpStatus.NOT_FOUND,
            "Пациент не найден",
            "Идентификатор пациента не существует в базе: пациент не был "
                    + "зарегистрирован либо ссылка устарела.",
            "Обновите список пациентов и повторите операцию из актуальной карточки. "
                    + "Если пациент должен существовать — зарегистрируйте его заново "
                    + "(POST /api/v1/patients)."),

    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR,
            "Внутренняя ошибка сервера",
            "Непредвиденное исключение на сервере; детали намеренно не раскрываются "
                    + "клиенту и записаны в лог приложения.",
            "Повторите запрос. Если ошибка воспроизводится — проверьте доступность "
                    + "PostgreSQL и логи контейнера app (docker compose logs app), "
                    + "приложите instance и время из ответа к обращению.");

    private final String slug;
    private final HttpStatus status;
    private final String title;
    private final String description;
    private final String remediation;

    ProblemType(String slug, HttpStatus status, String title, String description, String remediation) {
        this.slug = slug;
        this.status = status;
        this.title = title;
        this.description = description;
        this.remediation = remediation;
    }

    /** @return стабильный URI типа для поля {@code type} ответа RFC 9457 */
    public URI uri() {
        return URI.create("/problems/" + slug);
    }

    /** @return короткий идентификатор типа (последний сегмент URI) */
    public String getSlug() {
        return slug;
    }

    /** @return HTTP-статус, с которым возвращается этот класс проблем */
    public HttpStatus getStatus() {
        return status;
    }

    /** @return человекочитаемое имя класса проблемы (поле {@code title}) */
    public String getTitle() {
        return title;
    }

    /** @return что означает эта ошибка и когда возникает */
    public String getDescription() {
        return description;
    }

    /** @return действия для устранения ошибки */
    public String getRemediation() {
        return remediation;
    }

    /**
     * Тип проблемы по HTTP-статусу — для ошибок, приходящих как
     * {@code ResponseStatusException} без явного типа.
     *
     * @return тип или {@code null}, если статусу не сопоставлен известный тип
     */
    public static ProblemType forStatus(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> INVALID_REQUEST;
            case 401 -> INVALID_CREDENTIALS;
            case 403 -> ACCESS_DENIED;
            case 404 -> PATIENT_NOT_FOUND;
            case 500 -> INTERNAL_ERROR;
            default -> null;
        };
    }
}
