package maznin.monitoring.patient;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Реактивный доступ к таблице {@code measurements} (append-only).
 */
public interface MeasurementRepository extends ReactiveCrudRepository<Measurement, UUID> {

    /**
     * Последние {@code limit} измерений <i>по каждой метрике</i> пациента,
     * в хронологическом порядке — фронтенд добавляет их в буферы графиков
     * как есть. Оконная функция {@code row_number() OVER (PARTITION BY
     * metric ORDER BY measured_at DESC)} ограничивает выборку независимо
     * для каждой метрики, что недостижимо обычным {@code LIMIT}.
     *
     * @param patientId идентификатор пациента
     * @param limit максимум точек на метрику
     * @return измерения по возрастанию {@code measuredAt}
     */
    @Query("""
            SELECT id, patient_id, metric, value, measured_at FROM (
                SELECT m.*, row_number() OVER (PARTITION BY metric ORDER BY measured_at DESC) AS rn
                FROM measurements m
                WHERE patient_id = :patientId
            ) ranked
            WHERE rn <= :limit
            ORDER BY measured_at
            """)
    Flux<Measurement> findRecentByPatientId(UUID patientId, int limit);
}
