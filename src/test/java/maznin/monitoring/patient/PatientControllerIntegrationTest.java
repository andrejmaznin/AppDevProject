package maznin.monitoring.patient;

import maznin.monitoring.engine.MetricGenerationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientControllerIntegrationTest {

    private WebTestClient webTestClient;
    private PatientRepository patientRepository;
    private MetricGenerationEngineStub metricGenerationEngine;

    private static class PatientRepositoryStub implements PatientRepository {
        private final Map<UUID, Patient> patients = new HashMap<>();

        @Override
        public <S extends Patient> Mono<S> save(S entity) {
            patients.put(entity.getId(), entity);
            return Mono.just(entity);
        }

        @Override
        public Mono<Patient> findById(UUID uuid) {
            return Mono.justOrEmpty(patients.get(uuid));
        }

        @Override public <S extends Patient> Flux<S> saveAll(Iterable<S> entities) { return null; }
        @Override public <S extends Patient> Flux<S> saveAll(Publisher<S> entityStream) { return null; }
        @Override public Mono<Patient> findById(Publisher<UUID> id) { return null; }
        @Override public Mono<Boolean> existsById(UUID uuid) { return null; }
        @Override public Mono<Boolean> existsById(Publisher<UUID> id) { return null; }
        @Override public Flux<Patient> findAll() { return null; }
        @Override public Flux<Patient> findAllById(Iterable<UUID> uuids) { return null; }
        @Override public Flux<Patient> findAllById(Publisher<UUID> idStream) { return null; }
        @Override public Mono<Long> count() { return null; }
        @Override public Mono<Void> deleteById(UUID uuid) { return null; }
        @Override public Mono<Void> deleteById(Publisher<UUID> id) { return null; }
        @Override public Mono<Void> delete(Patient entity) { return null; }
        @Override public Mono<Void> deleteAllById(Iterable<? extends UUID> uuids) { return null; }
        @Override public Mono<Void> deleteAll(Iterable<? extends Patient> entities) { return null; }
        @Override public Mono<Void> deleteAll(Publisher<? extends Patient> entityStream) { return null; }
        @Override public Mono<Void> deleteAll() { return null; }
    }

    private static class MetricGenerationEngineStub extends MetricGenerationEngine {
        final Set<UUID> startedMonitoring = new HashSet<>();
        final Set<UUID> stoppedMonitoring = new HashSet<>();

        public MetricGenerationEngineStub() {
            super(null); // Pass null for repository, we won't use it
        }

        @Override
        public void startMonitoring(UUID patientId) {
            startedMonitoring.add(patientId);
        }

        @Override
        public void stopMonitoring(UUID patientId) {
            stoppedMonitoring.add(patientId);
        }
    }

    @BeforeEach
    void setUp() {
        patientRepository = new PatientRepositoryStub();
        metricGenerationEngine = new MetricGenerationEngineStub();
        
        // Quick dummy for StreamingService
        maznin.monitoring.api.StreamingService streamingService = new maznin.monitoring.api.StreamingService(null) {
            @Override
            public void init() {}
            @Override
            public Flux<maznin.monitoring.api.SenMLMeasurement> getStream(UUID id) {
                return Flux.empty();
            }
        };

        PatientService patientService = new PatientService(patientRepository, metricGenerationEngine);
        PatientController patientController = new PatientController(patientService, streamingService);
        
        webTestClient = WebTestClient.bindToController(patientController).build();
    }

    @Test
    void registerPatient_ValidRequest_ReturnsPatient() {
        PatientRequest request = new PatientRequest("John", "Doe");

        webTestClient.post()
                .uri("/api/v1/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.firstName").isEqualTo("John")
                .jsonPath("$.lastName").isEqualTo("Doe")
                .jsonPath("$.id").isNotEmpty();
    }

    @Test
    void startMonitoring_ExistingPatient_ReturnsOk() {
        UUID patientId = UUID.randomUUID();
        Patient patient = new Patient(patientId, "Jane", "Doe", false);
        ((PatientRepositoryStub) patientRepository).patients.put(patientId, patient);

        webTestClient.post()
                .uri("/api/v1/patients/{id}/monitoring/start", patientId)
                .exchange()
                .expectStatus().isOk();

        assertTrue(metricGenerationEngine.startedMonitoring.contains(patientId));
    }

    @Test
    void stopMonitoring_ExistingPatient_ReturnsOk() {
        UUID patientId = UUID.randomUUID();
        Patient patient = new Patient(patientId, "Jane", "Doe", true);
        ((PatientRepositoryStub) patientRepository).patients.put(patientId, patient);

        webTestClient.post()
                .uri("/api/v1/patients/{id}/monitoring/stop", patientId)
                .exchange()
                .expectStatus().isOk();

        assertTrue(metricGenerationEngine.stoppedMonitoring.contains(patientId));
    }
}
