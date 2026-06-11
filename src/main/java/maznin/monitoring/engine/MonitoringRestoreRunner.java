package maznin.monitoring.engine;

import maznin.monitoring.patient.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Восстановление мониторинга после перезапуска приложения.
 *
 * <p>Флаг {@code monitoringActive} хранится в БД, но сами задачи генерации
 * живут только в памяти. Этот раннер при старте контекста находит всех
 * пациентов с активным флагом и заново запускает для них генерацию —
 * перезапуск контейнера не прерывает наблюдение.</p>
 */
@Component
public class MonitoringRestoreRunner implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(MonitoringRestoreRunner.class);

    private final PatientRepository patientRepository;
    private final MetricGenerationEngine metricGenerationEngine;

    public MonitoringRestoreRunner(PatientRepository patientRepository, MetricGenerationEngine metricGenerationEngine) {
        this.patientRepository = patientRepository;
        this.metricGenerationEngine = metricGenerationEngine;
    }

    /**
     * Выполняется один раз после полной инициализации контекста Spring.
     * Подписка неблокирующая: старт приложения не ждёт обхода пациентов;
     * ошибка чтения БД логируется, но не валит приложение.
     *
     * @param args аргументы запуска (не используются)
     */
    @Override
    public void run(ApplicationArguments args) {
        patientRepository.findAll()
                .filter(p -> p.isMonitoringActive())
                .doOnNext(patient -> {
                    logger.info("Restoring monitoring for patient {}", patient.getId());
                    metricGenerationEngine.startMonitoring(patient.getId());
                })
                .subscribe(
                        p -> {},
                        e -> logger.error("Error restoring monitoring state on startup", e)
                );
    }
}
