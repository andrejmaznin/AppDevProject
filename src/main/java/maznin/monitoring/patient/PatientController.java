package maznin.monitoring.patient;

import maznin.monitoring.api.IncidentStreamingService;
import maznin.monitoring.api.MetricStatistics;
import maznin.monitoring.api.SenMLMeasurement;
import maznin.monitoring.api.StatisticsService;
import maznin.monitoring.api.StreamingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * REST-фасад API пациентов: {@code /api/v1/patients}.
 *
 * <p>Объединяет CRUD пациентов, управление мониторингом, выдачу истории,
 * статистики и два SSE-потока (измерения и критические инциденты). Все
 * эндпоинты требуют JWT; ошибки — в формате RFC 7807.</p>
 *
 * <table border="1">
 *   <caption>Сводка эндпоинтов</caption>
 *   <tr><th>Метод и путь</th><th>Назначение</th></tr>
 *   <tr><td>{@code POST /}</td><td>регистрация пациента</td></tr>
 *   <tr><td>{@code GET /}</td><td>список всех пациентов</td></tr>
 *   <tr><td>{@code GET /{id}}</td><td>один пациент</td></tr>
 *   <tr><td>{@code POST /{id}/monitoring/start|stop}</td><td>управление мониторингом</td></tr>
 *   <tr><td>{@code GET /{id}/statistics}</td><td>среднее, дисперсия, квартили на интервале</td></tr>
 *   <tr><td>{@code GET /{id}/measurements}</td><td>история последних измерений</td></tr>
 *   <tr><td>{@code GET /{id}/incidents}</td><td>история критических инцидентов</td></tr>
 *   <tr><td>{@code GET /{id}/stream}</td><td>SSE: измерения в реальном времени</td></tr>
 *   <tr><td>{@code GET /{id}/incidents/stream}</td><td>SSE: события инцидентов</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;
    private final StreamingService streamingService;
    private final StatisticsService statisticsService;
    private final MeasurementRepository measurementRepository;
    private final CriticalIncidentRepository criticalIncidentRepository;
    private final IncidentStreamingService incidentStreamingService;

    public PatientController(PatientService patientService, StreamingService streamingService,
                             StatisticsService statisticsService, MeasurementRepository measurementRepository,
                             CriticalIncidentRepository criticalIncidentRepository,
                             IncidentStreamingService incidentStreamingService) {
        this.patientService = patientService;
        this.streamingService = streamingService;
        this.statisticsService = statisticsService;
        this.measurementRepository = measurementRepository;
        this.criticalIncidentRepository = criticalIncidentRepository;
        this.incidentStreamingService = incidentStreamingService;
    }

    /**
     * Регистрирует нового пациента. Идентификатор (UUIDv7) присваивается
     * сервером; мониторинг изначально выключен.
     *
     * @param request имя и фамилия пациента
     * @return созданный пациент с присвоенным идентификатором
     */
    @PostMapping
    public Mono<Patient> registerPatient(@RequestBody PatientRequest request) {
        return patientService.registerPatient(request);
    }

    /**
     * Возвращает всех зарегистрированных пациентов с их текущим статусом
     * мониторинга.
     */
    @GetMapping
    public Flux<Patient> getAllPatients() {
        return patientService.getAllPatients();
    }

    /**
     * Возвращает пациента по идентификатору.
     *
     * @param id идентификатор пациента
     * @return пациент; 404 Problem Details, если не найден
     */
    @GetMapping("/{id}")
    public Mono<Patient> getPatient(@PathVariable UUID id) {
        return patientService.getPatient(id);
    }

    /**
     * Включает мониторинг: ставит флаг в БД и запускает задачи генерации
     * всех метрик. Повторный вызов безопасен (задачи не дублируются).
     *
     * @param id идентификатор пациента
     * @return пустой ответ 200; 404, если пациент не найден
     */
    @PostMapping("/{id}/monitoring/start")
    public Mono<Void> startMonitoring(@PathVariable UUID id) {
        return patientService.startMonitoring(id);
    }

    /**
     * Выключает мониторинг: снимает флаг в БД и останавливает задачи
     * генерации; открытые критические инциденты закрываются.
     *
     * @param id идентификатор пациента
     * @return пустой ответ 200; 404, если пациент не найден
     */
    @PostMapping("/{id}/monitoring/stop")
    public Mono<Void> stopMonitoring(@PathVariable UUID id) {
        return patientService.stopMonitoring(id);
    }

    /**
     * Статистические характеристики каждой метрики на интервале времени:
     * количество, среднее, выборочная дисперсия, квартили (Q1, медиана, Q3),
     * минимум и максимум. Метрики без измерений на интервале в ответ не
     * попадают.
     *
     * @param id идентификатор пациента
     * @param from начало интервала (RFC 3339); по умолчанию — час назад
     * @param to конец интервала, не включается; по умолчанию — сейчас
     * @return по одному элементу на метрику; 404, если пациент не найден
     */
    @GetMapping("/{id}/statistics")
    public Flux<MetricStatistics> getStatistics(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusHours(1);
        return patientService.getPatient(id)
                .thenMany(statisticsService.getStatistics(id, effectiveFrom, effectiveTo));
    }

    /**
     * История измерений: последние {@code limit} точек <i>по каждой метрике</i>
     * в хронологическом порядке — фронтенд предзаполняет ими графики при
     * открытии карточки пациента до прихода живого потока.
     *
     * @param id идентификатор пациента
     * @param limit максимум точек на каждую метрику (по умолчанию 100)
     * @return измерения в формате SenML; 404, если пациент не найден
     */
    @GetMapping("/{id}/measurements")
    public Flux<SenMLMeasurement> getMeasurements(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int limit) {
        return patientService.getPatient(id)
                .thenMany(measurementRepository.findRecentByPatientId(id, limit))
                .map(m -> new SenMLMeasurement(m.getMetric(), unitOf(m.getMetric()), m.getValue(), m.getMeasuredAt()));
    }

    /**
     * Единица измерения по строковому ключу метрики; пустая строка для
     * неизвестного ключа.
     */
    private static String unitOf(String metricKey) {
        for (Metric metric : Metric.values()) {
            if (metric.getKey().equals(metricKey)) return metric.getUnit();
        }
        return "";
    }

    /**
     * История критических инцидентов: последние 20 по убыванию времени
     * начала. Активные инциденты отличаются {@code resolvedAt == null}.
     *
     * @param id идентификатор пациента
     * @return инциденты; 404, если пациент не найден
     */
    @GetMapping("/{id}/incidents")
    public Flux<CriticalIncident> getIncidents(@PathVariable UUID id) {
        return patientService.getPatient(id)
                .thenMany(criticalIncidentRepository.findTop20ByPatientIdOrderByStartedAtDesc(id));
    }

    /**
     * SSE-поток событий критических инцидентов пациента: событие
     * {@code incident} эмитится в момент открытия (без {@code resolvedAt})
     * и в момент закрытия инцидента. Соединение держится открытым до
     * разрыва клиентом.
     *
     * @param id идентификатор пациента
     * @return бесконечный поток {@code event: incident}
     */
    @GetMapping(value = "/{id}/incidents/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CriticalIncident>> getIncidentStream(@PathVariable UUID id) {
        return incidentStreamingService.getStream(id)
                .map(incident -> ServerSentEvent.<CriticalIncident>builder()
                        .event("incident")
                        .data(incident)
                        .build());
    }

    /**
     * SSE-поток измерений пациента в реальном времени: событие {@code metric}
     * с телом SenML на каждое новое измерение. Источник — PostgreSQL
     * LISTEN/NOTIFY, поэтому в поток попадают только реально сохранённые
     * данные.
     *
     * @param id идентификатор пациента
     * @return бесконечный поток {@code event: metric}
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SenMLMeasurement>> getStream(@PathVariable UUID id) {
        return streamingService.getStream(id)
                .map(measurement -> ServerSentEvent.<SenMLMeasurement>builder()
                        .event("metric")
                        .data(measurement)
                        .build());
    }
}
