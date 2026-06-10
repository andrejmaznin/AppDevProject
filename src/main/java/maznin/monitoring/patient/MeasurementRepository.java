package maznin.monitoring.patient;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface MeasurementRepository extends ReactiveCrudRepository<Measurement, UUID> {

    // Last :limit points per metric, returned in chronological order so the
    // frontend can append them to chart buffers as-is
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
