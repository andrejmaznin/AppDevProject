package maznin.monitoring.patient;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface CriticalIncidentRepository extends ReactiveCrudRepository<CriticalIncident, UUID> {
    Flux<CriticalIncident> findTop20ByPatientIdOrderByStartedAtDesc(UUID patientId);
}
