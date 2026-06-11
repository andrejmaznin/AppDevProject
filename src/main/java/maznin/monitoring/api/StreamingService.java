package maznin.monitoring.api;

import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.Metric;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.UUID;

/**
 * Издатель измерений в реальном времени (паттерн Наблюдатель).
 *
 * <p>Архитектура «поток через БД»: генераторы пишут измерения только в
 * PostgreSQL; AFTER INSERT-триггер вызывает {@code pg_notify}, а этот сервис —
 * единственный долгоживущий слушатель канала {@code measurements_channel} —
 * раздаёт события всем подписчикам через {@code Sinks.Many}. Подписчики
 * гарантированно видят только реально сохранённые данные, а источник истины
 * один — БД.</p>
 *
 * <p>Sink — {@code multicast().directBestEffort()}: медленный SSE-клиент не
 * тормозит остальных, события для него отбрасываются.</p>
 */
@Service
public class StreamingService {

    private static final Logger logger = LoggerFactory.getLogger(StreamingService.class);
    private final ConnectionFactory connectionFactory;
    private final Sinks.Many<Measurement> sink = Sinks.many().multicast().directBestEffort();

    public StreamingService(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Инициализация после старта приложения: создаёт в БД функцию и триггер
     * {@code measurement_notify_trigger} (идемпотентно), выполняет
     * {@code LISTEN measurements_channel} на выделенном соединении и
     * перенаправляет входящие уведомления во внутренний sink.
     * {@code retry()} пересоздаёт подписку при обрыве соединения.
     * Для не-PostgreSQL БД (тесты на H2) — no-op.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (connectionFactory.getMetadata().getName().equalsIgnoreCase("PostgreSQL")) {
            logger.info("Initializing PostgreSQL LISTEN for measurements_channel");

            String triggerSql = """
                    CREATE OR REPLACE FUNCTION notify_measurement() RETURNS trigger AS $$
                    BEGIN
                        PERFORM pg_notify('measurements_channel', row_to_json(NEW)::text);
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql;

                    DROP TRIGGER IF EXISTS measurement_notify_trigger ON measurements;
                    
                    CREATE TRIGGER measurement_notify_trigger
                    AFTER INSERT ON measurements
                    FOR EACH ROW EXECUTE PROCEDURE notify_measurement();
                    """;

            Mono.from(connectionFactory.create())
                    .flatMap(conn -> {
                        PostgresqlConnection pgConn = unwrap(conn);
                        return pgConn.createStatement(triggerSql).execute()
                                .flatMap(PostgresqlResult::getRowsUpdated)
                                .then(Mono.just(pgConn));
                    })
                    .flatMapMany(pgConn -> pgConn.createStatement("LISTEN measurements_channel").execute()
                            .flatMap(PostgresqlResult::getRowsUpdated)
                            .thenMany(pgConn.getNotifications())
                    )
                    .map(notification -> {
                        String payload = notification.getParameter();
                        return parseMeasurement(payload);
                    })
                    .filter(m -> m != null)
                    .doOnNext(sink::tryEmitNext)
                    .doOnError(e -> logger.error("Error in Postgres LISTEN", e))
                    .retry()
                    .subscribe();
        } else {
            logger.info("Database is not PostgreSQL, skipping LISTEN setup.");
        }
    }

    /**
     * Разворачивает обёртки пулов соединений до нативного
     * {@code PostgresqlConnection} — только он даёт доступ к
     * {@code getNotifications()}.
     */
    private io.r2dbc.postgresql.api.PostgresqlConnection unwrap(io.r2dbc.spi.Connection connection) {
        if (connection instanceof io.r2dbc.postgresql.api.PostgresqlConnection) {
            return (io.r2dbc.postgresql.api.PostgresqlConnection) connection;
        }
        if (connection instanceof io.r2dbc.spi.Wrapped) {
            return unwrap(((io.r2dbc.spi.Wrapped<io.r2dbc.spi.Connection>) connection).unwrap());
        }
        throw new IllegalArgumentException("Cannot unwrap connection: " + connection.getClass());
    }

    /**
     * Разбирает JSON-payload уведомления pg_notify (результат
     * {@code row_to_json(NEW)}) в {@link Measurement}. Ошибочный payload
     * логируется и пропускается ({@code null} отфильтровывается выше).
     */
    private Measurement parseMeasurement(String json) {
        // Quick manual parse or use Jackson ObjectMapper
        // {"id":"...","patient_id":"...","metric":"...","value":75.2,"measured_at":"..."}
        // Since we are creating SenML from it, we just need patientId, metric, value, measuredAt
        try {
            // A quick hack since we don't have ObjectMapper injected here, 
            // but wait, we can just use Jackson ObjectMapper.
            org.springframework.boot.json.JacksonJsonParser parser = new org.springframework.boot.json.JacksonJsonParser();
            java.util.Map<String, Object> map = parser.parseMap(json);
            
            Measurement m = new Measurement();
            if (map.get("patient_id") != null) m.setPatientId(UUID.fromString(map.get("patient_id").toString()));
            if (map.get("metric") != null) m.setMetric(map.get("metric").toString());
            if (map.get("value") != null) m.setValue(Double.valueOf(map.get("value").toString()));
            if (map.get("measured_at") != null) m.setMeasuredAt(java.time.OffsetDateTime.parse(map.get("measured_at").toString()));
            
            return m;
        } catch (Exception e) {
            logger.error("Failed to parse notification payload: {}", json, e);
            return null;
        }
    }

    /** Прямая эмиссия в sink в обход LISTEN/NOTIFY — для тестов. */
    public void emitForTest(Measurement measurement) {
        sink.tryEmitNext(measurement);
    }

    /**
     * Поток измерений одного пациента в формате SenML. Каждый вызов — новая
     * подписка на общий sink с фильтром по {@code patientId}; завершается
     * только отпиской клиента.
     *
     * @param patientId идентификатор пациента
     * @return бесконечный поток измерений пациента
     */
    public Flux<SenMLMeasurement> getStream(UUID patientId) {
        return sink.asFlux()
                .filter(m -> m.getPatientId().equals(patientId))
                .map(m -> {
                    String unit = "";
                    try {
                        unit = Metric.valueOf(m.getMetric().toUpperCase()).getUnit();
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                    return new SenMLMeasurement(m.getMetric(), unit, m.getValue(), m.getMeasuredAt());
                });
    }
}
