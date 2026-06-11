package maznin.monitoring.patient;

import maznin.monitoring.engine.MetricGenerationEngine;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Бизнес-операции над пациентами.
 *
 * <p>Связывает два мира: персистентное состояние ({@code monitoringActive}
 * в таблице {@code patients}) и рантайм-состояние (задачи генерации в
 * {@link MetricGenerationEngine}). Инвариант «флаг в БД соответствует
 * работающим задачам» поддерживается порядком операций: сначала сохранение
 * флага, затем управление движком; после рестарта его восстанавливает
 * {@code MonitoringRestoreRunner}.</p>
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final MetricGenerationEngine metricGenerationEngine;

    public PatientService(PatientRepository patientRepository, MetricGenerationEngine metricGenerationEngine) {
        this.patientRepository = patientRepository;
        this.metricGenerationEngine = metricGenerationEngine;
    }

    /**
     * Создаёт пациента с серверным идентификатором UUIDv7 и выключенным
     * мониторингом.
     *
     * @param request имя и фамилия
     * @return сохранённый пациент
     */
    public Mono<Patient> registerPatient(PatientRequest request) {
        Patient patient = new Patient(
                UuidCreator.getTimeOrderedEpoch(),
                request.getFirstName(),
                request.getLastName(),
                false
        );
        return patientRepository.save(patient);
    }

    /** @return все зарегистрированные пациенты */
    public Flux<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    /**
     * Пациент по идентификатору.
     *
     * @param id идентификатор
     * @return пациент; {@code ResponseStatusException} 404, если не найден
     */
    public Mono<Patient> getPatient(UUID id) {
        return patientRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found")));
    }

    /**
     * Включает мониторинг: фиксирует флаг в БД, затем запускает задачи
     * генерации. Запуск движка происходит в {@code doOnNext} после успешного
     * сохранения — если запись не удалась, задачи не стартуют.
     *
     * @param patientId идентификатор пациента
     * @return завершение операции; 404, если пациент не найден
     */
    public Mono<Void> startMonitoring(UUID patientId) {
        return patientRepository.findById(patientId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found")))
                .flatMap(patient -> {
                    patient.setMonitoringActive(true);
                    return patientRepository.save(patient);
                })
                .doOnNext(patient -> metricGenerationEngine.startMonitoring(patient.getId()))
                .then();
    }

    /**
     * Выключает мониторинг: снимает флаг в БД, затем останавливает задачи.
     * Открытые критические инциденты закрываются самими задачами при
     * завершении.
     *
     * @param patientId идентификатор пациента
     * @return завершение операции; 404, если пациент не найден
     */
    public Mono<Void> stopMonitoring(UUID patientId) {
        return patientRepository.findById(patientId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found")))
                .flatMap(patient -> {
                    patient.setMonitoringActive(false);
                    return patientRepository.save(patient);
                })
                .doOnNext(patient -> metricGenerationEngine.stopMonitoring(patient.getId()))
                .then();
    }
}
