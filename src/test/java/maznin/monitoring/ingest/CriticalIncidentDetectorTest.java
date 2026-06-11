package maznin.monitoring.ingest;

import maznin.monitoring.api.IncidentStreamingService;
import maznin.monitoring.patient.CriticalIncident;
import maznin.monitoring.patient.CriticalIncidentRepository;
import maznin.monitoring.patient.Measurement;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Детектор тестируется синхронно: измерения подаются напрямую, без задач,
 * тиков и потоков — сценарии детерминированы и выполняются мгновенно.
 */
class CriticalIncidentDetectorTest {

    /** Снимок состояния инцидента в момент события (сам объект мутирует). */
    private record IncidentSnapshot(UUID id, boolean resolved, Double maxDeviationValue) {
        static IncidentSnapshot of(CriticalIncident incident) {
            return new IncidentSnapshot(incident.getId(),
                    incident.getResolvedAt() != null, incident.getMaxDeviationValue());
        }
    }

    private static class RecordingIncidentRepository implements CriticalIncidentRepository {
        final List<IncidentSnapshot> saves = new CopyOnWriteArrayList<>();

        @Override
        public <S extends CriticalIncident> Mono<S> save(S entity) {
            saves.add(IncidentSnapshot.of(entity));
            return Mono.just(entity);
        }

        @Override public Flux<CriticalIncident> findTop20ByPatientIdOrderByStartedAtDesc(UUID patientId) { return Flux.empty(); }
        @Override public <S extends CriticalIncident> Flux<S> saveAll(Iterable<S> entities) { return Flux.empty(); }
        @Override public <S extends CriticalIncident> Flux<S> saveAll(Publisher<S> entityStream) { return Flux.empty(); }
        @Override public Mono<CriticalIncident> findById(UUID uuid) { return Mono.empty(); }
        @Override public Mono<CriticalIncident> findById(Publisher<UUID> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(UUID uuid) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<UUID> id) { return Mono.just(false); }
        @Override public Flux<CriticalIncident> findAll() { return Flux.empty(); }
        @Override public Flux<CriticalIncident> findAllById(Iterable<UUID> uuids) { return Flux.empty(); }
        @Override public Flux<CriticalIncident> findAllById(Publisher<UUID> idStream) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(UUID uuid) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<UUID> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(CriticalIncident entity) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends UUID> uuids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends CriticalIncident> entities) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends CriticalIncident> entityStream) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
    }

    private final RecordingIncidentRepository repository = new RecordingIncidentRepository();
    private final IncidentStreamingService streaming = new IncidentStreamingService();
    private final CriticalIncidentDetector detector = new CriticalIncidentDetector(repository, streaming);

    private final UUID patientId = UUID.randomUUID();
    private final OffsetDateTime base = OffsetDateTime.parse("2026-06-11T10:00:00Z");

    private List<IncidentSnapshot> subscribeEvents() {
        List<IncidentSnapshot> events = new CopyOnWriteArrayList<>();
        streaming.getStream(patientId).subscribe(i -> events.add(IncidentSnapshot.of(i)));
        return events;
    }

    private void feed(String metric, double... values) {
        for (int i = 0; i < values.length; i++) {
            detector.onMeasurement(new Measurement(
                    UUID.randomUUID(), patientId, metric, values[i], base.plusSeconds(i)));
        }
    }

    @Test
    void incidentIsOpenedTrackedAndResolved() {
        List<IncidentSnapshot> events = subscribeEvents();

        // Норма пульса 60–90: 100 открывает, 120 — новый пик, 75 закрывает
        feed("heart_rate", 100.0, 120.0, 75.0);

        assertEquals(2, events.size(), "Exactly two events: open and resolve");
        assertFalse(events.get(0).resolved());
        assertEquals(100.0, events.get(0).maxDeviationValue(), "Opening event carries the first out-of-range value");
        assertTrue(events.get(1).resolved());
        assertEquals(events.get(0).id(), events.get(1).id(), "Both events refer to the same incident");
        assertEquals(120.0, events.get(1).maxDeviationValue(), "Peak deviation tracked across the episode");

        assertEquals(2, repository.saves.size(), "INSERT on open + UPDATE on resolve");
        assertFalse(repository.saves.get(0).resolved());
        assertTrue(repository.saves.get(1).resolved());
    }

    @Test
    void noIncidentWhileValuesStayInRange() {
        feed("heart_rate", 75.0, 88.0, 61.0);

        assertTrue(repository.saves.isEmpty(), "In-range values must not open incidents");
    }

    @Test
    void deviationTracksFarthestValueBelowRange() {
        List<IncidentSnapshot> events = subscribeEvents();

        // Просадка ниже нормы: 50 → 40 (дальше всего от mu=75) → 45 → возврат
        feed("heart_rate", 50.0, 40.0, 45.0, 75.0);

        assertEquals(40.0, events.get(1).maxDeviationValue(),
                "Peak is the farthest value from mu, not the latest out-of-range one");
    }

    @Test
    void streamClosedResolvesOpenIncident() {
        feed("heart_rate", 110.0, 115.0);

        detector.onStreamClosed(patientId, "heart_rate");

        assertEquals(2, repository.saves.size(), "INSERT on open + UPDATE on stream close");
        IncidentSnapshot last = repository.saves.get(1);
        assertTrue(last.resolved());
        assertNotNull(last.maxDeviationValue());
    }

    @Test
    void streamClosedWithoutOpenIncidentIsNoop() {
        feed("heart_rate", 75.0);

        detector.onStreamClosed(patientId, "heart_rate");

        assertTrue(repository.saves.isEmpty());
    }

    @Test
    void episodesOfDifferentMetricsAreIndependent() {
        // Пульс вне нормы, температура в норме (36.5–37.2)
        feed("heart_rate", 110.0);
        feed("temperature", 36.7);

        assertEquals(1, repository.saves.size(), "Only the heart_rate episode is open");

        // Возврат пульса в норму закрывает только его эпизод
        feed("heart_rate", 75.0);
        assertEquals(2, repository.saves.size());
        assertTrue(repository.saves.get(1).resolved());
    }

    @Test
    void unknownMetricIsIgnored() {
        feed("not_a_metric", 9999.0);

        assertTrue(repository.saves.isEmpty(), "Unknown metric keys must be ignored");
    }
}
