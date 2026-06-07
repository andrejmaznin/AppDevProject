package maznin.monitoring.patient;

import maznin.monitoring.engine.MetricGenerationEngine;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final MetricGenerationEngine metricGenerationEngine;

    public PatientService(PatientRepository patientRepository, MetricGenerationEngine metricGenerationEngine) {
        this.patientRepository = patientRepository;
        this.metricGenerationEngine = metricGenerationEngine;
    }

    public Mono<Patient> registerPatient(PatientRequest request) {
        Patient patient = new Patient(
                UuidCreator.getTimeOrderedEpoch(),
                request.getFirstName(),
                request.getLastName(),
                false
        );
        return patientRepository.save(patient);
    }

    public Mono<Void> startMonitoring(UUID patientId) {
        return patientRepository.findById(patientId)
                .flatMap(patient -> {
                    patient.setMonitoringActive(true);
                    return patientRepository.save(patient);
                })
                .doOnNext(patient -> metricGenerationEngine.startMonitoring(patient.getId()))
                .then();
    }

    public Mono<Void> stopMonitoring(UUID patientId) {
        return patientRepository.findById(patientId)
                .flatMap(patient -> {
                    patient.setMonitoringActive(false);
                    return patientRepository.save(patient);
                })
                .doOnNext(patient -> metricGenerationEngine.stopMonitoring(patient.getId()))
                .then();
    }
}
