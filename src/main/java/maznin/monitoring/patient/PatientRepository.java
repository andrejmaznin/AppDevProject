package maznin.monitoring.patient;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/**
 * Реактивный доступ к таблице {@code patients}.
 *
 * <p>Достаточно стандартных CRUD-операций: выборка всех пациентов (список в
 * интерфейсе, восстановление мониторинга при старте), поиск по идентификатору
 * и сохранение.</p>
 */
public interface PatientRepository extends ReactiveCrudRepository<Patient, UUID> {
}
