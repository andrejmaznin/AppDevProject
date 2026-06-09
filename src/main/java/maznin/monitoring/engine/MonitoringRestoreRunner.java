package maznin.monitoring.engine;

import maznin.monitoring.patient.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MonitoringRestoreRunner implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(MonitoringRestoreRunner.class);

    private final PatientRepository patientRepository;
    private final MetricGenerationEngine metricGenerationEngine;

    public MonitoringRestoreRunner(PatientRepository patientRepository, MetricGenerationEngine metricGenerationEngine) {
        this.patientRepository = patientRepository;
        this.metricGenerationEngine = metricGenerationEngine;
    }

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
