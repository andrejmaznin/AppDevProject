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
    private MeasurementRepositoryStub measurementRepository;
    private final java.util.List<CriticalIncident> incidents = new java.util.ArrayList<>();

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

    private static class MeasurementRepositoryStub implements MeasurementRepository {
        final java.util.List<Measurement> measurements = new java.util.ArrayList<>();

        @Override
        public Flux<Measurement> findRecentByPatientId(UUID patientId, int limit) {
            return Flux.fromIterable(measurements.stream()
                    .filter(m -> m.getPatientId().equals(patientId))
                    .sorted(java.util.Comparator.comparing(Measurement::getMeasuredAt))
                    .toList());
        }

        @Override public <S extends Measurement> Mono<S> save(S entity) { return null; }
        @Override public <S extends Measurement> Flux<S> saveAll(Iterable<S> entities) { return null; }
        @Override public <S extends Measurement> Flux<S> saveAll(Publisher<S> entityStream) { return null; }
        @Override public Mono<Measurement> findById(UUID uuid) { return null; }
        @Override public Mono<Measurement> findById(Publisher<UUID> id) { return null; }
        @Override public Mono<Boolean> existsById(UUID uuid) { return null; }
        @Override public Mono<Boolean> existsById(Publisher<UUID> id) { return null; }
        @Override public Flux<Measurement> findAll() { return null; }
        @Override public Flux<Measurement> findAllById(Iterable<UUID> uuids) { return null; }
        @Override public Flux<Measurement> findAllById(Publisher<UUID> idStream) { return null; }
        @Override public Mono<Long> count() { return null; }
        @Override public Mono<Void> deleteById(UUID uuid) { return null; }
        @Override public Mono<Void> deleteById(Publisher<UUID> id) { return null; }
        @Override public Mono<Void> delete(Measurement entity) { return null; }
        @Override public Mono<Void> deleteAllById(Iterable<? extends UUID> uuids) { return null; }
        @Override public Mono<Void> deleteAll(Iterable<? extends Measurement> entities) { return null; }
        @Override public Mono<Void> deleteAll(Publisher<? extends Measurement> entityStream) { return null; }
        @Override public Mono<Void> deleteAll() { return null; }
    }

    private static class MetricGenerationEngineStub extends MetricGenerationEngine {
        final Set<UUID> startedMonitoring = new HashSet<>();
        final Set<UUID> stoppedMonitoring = new HashSet<>();

        public MetricGenerationEngineStub() {
            super(null); // No ingest service needed, we won't run tasks
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

        maznin.monitoring.api.StatisticsService statisticsService = new maznin.monitoring.api.StatisticsService(null) {
            @Override
            public Flux<maznin.monitoring.api.MetricStatistics> getStatistics(
                    UUID patientId, java.time.OffsetDateTime from, java.time.OffsetDateTime to) {
                return Flux.empty();
            }
        };

        measurementRepository = new MeasurementRepositoryStub();

        maznin.monitoring.patient.CriticalIncidentRepository incidentRepository =
                new maznin.monitoring.patient.CriticalIncidentRepository() {
            @Override public <S extends maznin.monitoring.patient.CriticalIncident> reactor.core.publisher.Mono<S> save(S e) { return reactor.core.publisher.Mono.just(e); }
            @Override public <S extends maznin.monitoring.patient.CriticalIncident> reactor.core.publisher.Flux<S> saveAll(Iterable<S> e) { return reactor.core.publisher.Flux.empty(); }
            @Override public <S extends maznin.monitoring.patient.CriticalIncident> reactor.core.publisher.Flux<S> saveAll(org.reactivestreams.Publisher<S> e) { return reactor.core.publisher.Flux.empty(); }
            @Override public reactor.core.publisher.Mono<maznin.monitoring.patient.CriticalIncident> findById(UUID id) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<maznin.monitoring.patient.CriticalIncident> findById(org.reactivestreams.Publisher<UUID> id) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<Boolean> existsById(UUID id) { return reactor.core.publisher.Mono.just(false); }
            @Override public reactor.core.publisher.Mono<Boolean> existsById(org.reactivestreams.Publisher<UUID> id) { return reactor.core.publisher.Mono.just(false); }
            @Override public reactor.core.publisher.Flux<maznin.monitoring.patient.CriticalIncident> findAll() { return reactor.core.publisher.Flux.empty(); }
            @Override public reactor.core.publisher.Flux<maznin.monitoring.patient.CriticalIncident> findAllById(Iterable<UUID> ids) { return reactor.core.publisher.Flux.empty(); }
            @Override public reactor.core.publisher.Flux<maznin.monitoring.patient.CriticalIncident> findAllById(org.reactivestreams.Publisher<UUID> ids) { return reactor.core.publisher.Flux.empty(); }
            @Override public reactor.core.publisher.Mono<Long> count() { return reactor.core.publisher.Mono.just(0L); }
            @Override public reactor.core.publisher.Mono<Void> deleteById(UUID id) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<Void> deleteById(org.reactivestreams.Publisher<UUID> id) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<Void> delete(maznin.monitoring.patient.CriticalIncident e) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<Void> deleteAllById(Iterable<? extends UUID> ids) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<Void> deleteAll(Iterable<? extends maznin.monitoring.patient.CriticalIncident> e) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<Void> deleteAll(org.reactivestreams.Publisher<? extends maznin.monitoring.patient.CriticalIncident> e) { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Mono<Void> deleteAll() { return reactor.core.publisher.Mono.empty(); }
            @Override public reactor.core.publisher.Flux<maznin.monitoring.patient.CriticalIncident> findTop20ByPatientIdOrderByStartedAtDesc(UUID patientId) {
                return reactor.core.publisher.Flux.fromIterable(incidents.stream()
                        .filter(i -> i.getPatientId().equals(patientId)).toList());
            }
        };

        maznin.monitoring.api.IncidentStreamingService incidentStreamingService =
                new maznin.monitoring.api.IncidentStreamingService();

        PatientService patientService = new PatientService(patientRepository, metricGenerationEngine);
        PatientController patientController = new PatientController(patientService, streamingService, statisticsService,
                measurementRepository, incidentRepository, incidentStreamingService);
        
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
    void getMeasurements_ExistingPatient_ReturnsSenMLHistoryInChronologicalOrder() {
        UUID patientId = UUID.randomUUID();
        ((PatientRepositoryStub) patientRepository).patients
                .put(patientId, new Patient(patientId, "Jane", "Doe", true));

        java.time.OffsetDateTime base = java.time.OffsetDateTime.parse("2026-06-10T10:00:00Z");
        measurementRepository.measurements.add(
                new Measurement(UUID.randomUUID(), patientId, "heart_rate", 80.0, base.plusSeconds(1)));
        measurementRepository.measurements.add(
                new Measurement(UUID.randomUUID(), patientId, "heart_rate", 75.0, base));
        measurementRepository.measurements.add(
                new Measurement(UUID.randomUUID(), UUID.randomUUID(), "heart_rate", 99.0, base)); // other patient

        webTestClient.get()
                .uri("/api/v1/patients/{id}/measurements?limit=100", patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].n").isEqualTo("heart_rate")
                .jsonPath("$[0].u").isEqualTo("bpm")
                .jsonPath("$[0].v").isEqualTo(75.0)
                .jsonPath("$[0].t").isEqualTo("2026-06-10T10:00:00Z")
                .jsonPath("$[1].v").isEqualTo(80.0);
    }

    @Test
    void getMeasurements_UnknownPatient_ReturnsNotFound() {
        webTestClient.get()
                .uri("/api/v1/patients/{id}/measurements", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void startMonitoring_UnknownPatient_ReturnsNotFound() {
        webTestClient.post()
                .uri("/api/v1/patients/{id}/monitoring/start", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();

        assertTrue(metricGenerationEngine.startedMonitoring.isEmpty());
    }

    @Test
    void getIncidents_ExistingPatient_ReturnsIncidentsOfThatPatientOnly() {
        UUID patientId = UUID.randomUUID();
        ((PatientRepositoryStub) patientRepository).patients
                .put(patientId, new Patient(patientId, "Jane", "Doe", true));

        java.time.OffsetDateTime startedAt = java.time.OffsetDateTime.parse("2026-06-10T10:00:00Z");
        CriticalIncident active = new CriticalIncident(
                UUID.randomUUID(), patientId, "heart_rate", startedAt, 120.0);
        incidents.add(active);
        incidents.add(new CriticalIncident(
                UUID.randomUUID(), UUID.randomUUID(), "cvp", startedAt, 9.5)); // другой пациент

        webTestClient.get()
                .uri("/api/v1/patients/{id}/incidents", patientId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(active.getId().toString())
                .jsonPath("$[0].metric").isEqualTo("heart_rate")
                .jsonPath("$[0].maxDeviationValue").isEqualTo(120.0)
                .jsonPath("$[0].resolvedAt").doesNotExist();
    }

    @Test
    void getIncidents_UnknownPatient_ReturnsNotFound() {
        webTestClient.get()
                .uri("/api/v1/patients/{id}/incidents", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
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
