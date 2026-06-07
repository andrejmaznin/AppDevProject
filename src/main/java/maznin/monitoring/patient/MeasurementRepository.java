package maznin.monitoring.patient;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface MeasurementRepository extends ReactiveCrudRepository<Measurement, UUID> {
}
