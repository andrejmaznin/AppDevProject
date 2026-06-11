package maznin.monitoring.engine;

import maznin.monitoring.api.IncidentStreamingService;
import maznin.monitoring.patient.CriticalIncident;
import maznin.monitoring.patient.CriticalIncidentRepository;
import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.MeasurementRepository;
import maznin.monitoring.patient.Metric;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalIncidentLifecycleTest {

    /** Снимок состояния инцидента в момент publish (сам объект мутирует между событиями). */
    private record IncidentSnapshot(UUID id, boolean resolved, Double maxDeviationValue) {
        static IncidentSnapshot of(CriticalIncident incident) {
            return new IncidentSnapshot(incident.getId(),
                    incident.getResolvedAt() != null, incident.getMaxDeviationValue());
        }
    }

    /** Детерминированная стратегия: выдаёт значения по сценарию, затем держит последнее. */
    private static class ScriptedGenerator implements ValueGenerator {
        private final double[] script;
        private final AtomicInteger index = new AtomicInteger();

        ScriptedGenerator(double... script) {
            this.script = script;
        }

        @Override
        public double next(double currentValue, double dtSeconds) {
            return script[Math.min(index.getAndIncrement(), script.length - 1)];
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

    private static class NoopMeasurementRepository implements MeasurementRepository {
        @Override public <S extends Measurement> Mono<S> save(S entity) { return Mono.just(entity); }
        @Override public Flux<Measurement> findRecentByPatientId(UUID patientId, int limit) { return Flux.empty(); }
        @Override public <S extends Measurement> Flux<S> saveAll(Iterable<S> entities) { return Flux.empty(); }
        @Override public <S extends Measurement> Flux<S> saveAll(Publisher<S> entityStream) { return Flux.empty(); }
        @Override public Mono<Measurement> findById(UUID uuid) { return Mono.empty(); }
        @Override public Mono<Measurement> findById(Publisher<UUID> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(UUID uuid) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<UUID> id) { return Mono.just(false); }
        @Override public Flux<Measurement> findAll() { return Flux.empty(); }
        @Override public Flux<Measurement> findAllById(Iterable<UUID> uuids) { return Flux.empty(); }
        @Override public Flux<Measurement> findAllById(Publisher<UUID> idStream) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(UUID uuid) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<UUID> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(Measurement entity) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends UUID> uuids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends Measurement> entities) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends Measurement> entityStream) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
    }

    @Test
    void incidentIsOpenedTrackedAndResolved() throws InterruptedException {
        UUID patientId = UUID.randomUUID();
        RecordingIncidentRepository incidentRepository = new RecordingIncidentRepository();
        IncidentStreamingService streamingService = new IncidentStreamingService();

        List<IncidentSnapshot> events = new CopyOnWriteArrayList<>();
        CountDownLatch twoEvents = new CountDownLatch(2);
        streamingService.getStream(patientId).subscribe(incident -> {
            events.add(IncidentSnapshot.of(incident));
            twoEvents.countDown();
        });

        // Норма пульса 60–90: 100 открывает инцидент, 120 — новый пик, 75 закрывает
        MetricGeneratorTask task = new MetricGeneratorTask(
                patientId, Metric.HEART_RATE,
                new NoopMeasurementRepository(), incidentRepository, streamingService,
                new ScriptedGenerator(100.0, 120.0, 75.0));
        Thread thread = Thread.ofVirtual().start(task);

        assertTrue(twoEvents.await(10, TimeUnit.SECONDS),
                "Expected incident start and resolve events within 10s");
        task.stop();
        thread.join(2000);

        assertEquals(2, events.size(), "Exactly two events: start and resolve");

        IncidentSnapshot started = events.get(0);
        assertFalse(started.resolved(), "First event is the open incident");
        assertEquals(100.0, started.maxDeviationValue(), "Initial deviation is the first out-of-range value");

        IncidentSnapshot resolved = events.get(1);
        assertTrue(resolved.resolved(), "Second event is the resolved incident");
        assertEquals(started.id(), resolved.id(), "Both events refer to the same incident");
        assertEquals(120.0, resolved.maxDeviationValue(), "Peak deviation tracked across the incident");

        // Два сохранения: INSERT при открытии (без resolvedAt) и UPDATE при закрытии
        assertEquals(2, incidentRepository.saves.size());
        assertFalse(incidentRepository.saves.get(0).resolved());
        assertTrue(incidentRepository.saves.get(1).resolved());
    }

    @Test
    void noIncidentWhileValuesStayInRange() throws InterruptedException {
        UUID patientId = UUID.randomUUID();
        RecordingIncidentRepository incidentRepository = new RecordingIncidentRepository();
        IncidentStreamingService streamingService = new IncidentStreamingService();

        CountDownLatch threeTicks = new CountDownLatch(3);
        MeasurementRepository countingRepository = new NoopMeasurementRepository() {
            @Override
            public <S extends Measurement> Mono<S> save(S entity) {
                threeTicks.countDown();
                return Mono.just(entity);
            }
        };

        MetricGeneratorTask task = new MetricGeneratorTask(
                patientId, Metric.HEART_RATE,
                countingRepository, incidentRepository, streamingService,
                new ScriptedGenerator(75.0, 88.0, 61.0));
        Thread thread = Thread.ofVirtual().start(task);

        assertTrue(threeTicks.await(10, TimeUnit.SECONDS));
        task.stop();
        thread.join(2000);

        assertTrue(incidentRepository.saves.isEmpty(), "In-range values must not open incidents");
    }

    @Test
    void openIncidentIsResolvedWhenMonitoringStops() throws InterruptedException {
        UUID patientId = UUID.randomUUID();
        RecordingIncidentRepository incidentRepository = new RecordingIncidentRepository();
        IncidentStreamingService streamingService = new IncidentStreamingService();

        CountDownLatch opened = new CountDownLatch(1);
        streamingService.getStream(patientId).subscribe(i -> opened.countDown());

        // Значение всё время вне нормы — инцидент закрывается только остановкой
        MetricGeneratorTask task = new MetricGeneratorTask(
                patientId, Metric.HEART_RATE,
                new NoopMeasurementRepository(), incidentRepository, streamingService,
                new ScriptedGenerator(110.0));
        Thread thread = Thread.ofVirtual().start(task);

        assertTrue(opened.await(10, TimeUnit.SECONDS), "Incident must open on first tick");
        task.stop();
        thread.join(3000);
        assertFalse(thread.isAlive(), "Task thread must terminate after stop()");

        assertEquals(2, incidentRepository.saves.size(), "INSERT on open + UPDATE on stop-resolve");
        IncidentSnapshot last = incidentRepository.saves.get(1);
        assertTrue(last.resolved(), "stop() must resolve the open incident");
        assertNotNull(last.maxDeviationValue());
    }

    @Test
    void deviationTracksFarthestValueBelowRange() throws InterruptedException {
        UUID patientId = UUID.randomUUID();
        RecordingIncidentRepository incidentRepository = new RecordingIncidentRepository();
        IncidentStreamingService streamingService = new IncidentStreamingService();

        List<IncidentSnapshot> events = new CopyOnWriteArrayList<>();
        CountDownLatch twoEvents = new CountDownLatch(2);
        streamingService.getStream(patientId).subscribe(incident -> {
            events.add(IncidentSnapshot.of(incident));
            twoEvents.countDown();
        });

        // Просадка ниже нормы: 50 → 40 (дальше от mu=75) → 45 → возврат
        MetricGeneratorTask task = new MetricGeneratorTask(
                patientId, Metric.HEART_RATE,
                new NoopMeasurementRepository(), incidentRepository, streamingService,
                new ScriptedGenerator(50.0, 40.0, 45.0, 75.0));
        Thread thread = Thread.ofVirtual().start(task);

        assertTrue(twoEvents.await(15, TimeUnit.SECONDS));
        task.stop();
        thread.join(2000);

        assertEquals(50.0, events.get(0).maxDeviationValue(),
                "Opening event carries the first out-of-range value");
        assertEquals(40.0, events.get(1).maxDeviationValue(),
                "Peak is the farthest value from mu, not the latest out-of-range one");
    }
}
