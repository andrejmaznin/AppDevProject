/**
 * Доменная модель: пациенты, измерения, критические инциденты.
 *
 * <h2>Назначение</h2>
 * Центральный пакет предметной области. Содержит сущности, репозитории
 * (Spring Data R2DBC), бизнес-сервис пациентов и REST-контроллер — единый
 * фасад API {@code /api/v1/patients}.
 *
 * <h2>Состав</h2>
 * <ul>
 *   <li>Сущности: {@link maznin.monitoring.patient.Patient},
 *       {@link maznin.monitoring.patient.Measurement},
 *       {@link maznin.monitoring.patient.CriticalIncident};</li>
 *   <li>{@link maznin.monitoring.patient.Metric} — перечисление метрик с их
 *       физиологическими параметрами (μ, σ, норма, частота измерений);</li>
 *   <li>Репозитории: {@link maznin.monitoring.patient.PatientRepository},
 *       {@link maznin.monitoring.patient.MeasurementRepository},
 *       {@link maznin.monitoring.patient.CriticalIncidentRepository};</li>
 *   <li>{@link maznin.monitoring.patient.PatientService} — бизнес-операции
 *       (регистрация, старт/стоп мониторинга);</li>
 *   <li>{@link maznin.monitoring.patient.PatientController} — REST + SSE
 *       эндпоинты;</li>
 *   <li>{@link maznin.monitoring.patient.PatientRequest} — DTO регистрации.</li>
 * </ul>
 *
 * <h2>Паттерн Persistable</h2>
 * Идентификаторы сущностей — UUIDv7, присваиваются в коде приложения
 * (а не БД). Spring Data R2DBC по умолчанию считает запись с непустым
 * {@code @Id} существующей и выполняет UPDATE. Поэтому сущности реализуют
 * {@code Persistable<UUID>} с {@code @Transient}-флагом {@code newEntity}:
 * конструктор со всеми аргументами помечает объект новым (будет INSERT),
 * {@code markNotNew()} переводит в существующий (последующие save — UPDATE).
 * {@code isNew()} помечен {@code @JsonIgnore}, чтобы служебный флаг не
 * попадал в JSON API.
 */
package maznin.monitoring.patient;
