package maznin.monitoring.patient;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Реактивный доступ к таблице {@code critical_incidents}.
 */
public interface CriticalIncidentRepository extends ReactiveCrudRepository<CriticalIncident, UUID> {

    /**
     * Последние 20 инцидентов пациента, новейшие первыми (по времени начала).
     * Запрос выводится Spring Data из имени метода.
     *
     * @param patientId идентификатор пациента
     * @return инциденты по убыванию {@code startedAt}; активные содержат
     *         {@code resolvedAt == null}
     */
    Flux<CriticalIncident> findTop20ByPatientIdOrderByStartedAtDesc(UUID patientId);
}
