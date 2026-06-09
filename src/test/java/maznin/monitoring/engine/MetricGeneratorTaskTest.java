package maznin.monitoring.engine;

import maznin.monitoring.patient.CriticalIncident;
import maznin.monitoring.patient.CriticalIncidentRepository;
import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.MeasurementRepository;
import maznin.monitoring.patient.Metric;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricGeneratorTaskTest {

    private static class MeasurementRepositoryStub implements MeasurementRepository {
        final List<Measurement> savedMeasurements = new ArrayList<>();
        private final CountDownLatch latch;

        MeasurementRepositoryStub(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public <S extends Measurement> Mono<S> save(S entity) {
            savedMeasurements.add(entity);
            latch.countDown();
            return Mono.just(entity);
        }

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

    private static class CriticalIncidentRepositoryStub implements CriticalIncidentRepository {
        @Override public <S extends CriticalIncident> Mono<S> save(S entity) { return Mono.just(entity); }
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

    @Test
    void testMetricGeneration() throws InterruptedException {
        UUID patientId = UUID.randomUUID();
        Metric metric = Metric.HEART_RATE;
        CountDownLatch latch = new CountDownLatch(3);
        MeasurementRepositoryStub stub = new MeasurementRepositoryStub(latch);

        MetricGeneratorTask task = new MetricGeneratorTask(
                patientId, metric, stub, new CriticalIncidentRepositoryStub());
        Thread thread = Thread.ofVirtual().start(task);

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        task.stop();
        thread.join(1000);

        assertTrue(completed, "Should have generated at least 3 measurements");
        assertFalse(stub.savedMeasurements.isEmpty(), "Measurements list should not be empty");

        for (Measurement m : stub.savedMeasurements) {
            assertTrue(m.getValue() > 0, "Generated value should be positive");
            assertTrue(m.getValue() > metric.getRangeMin() - 10, "Value too low: " + m.getValue());
            assertTrue(m.getValue() < metric.getRangeMax() + 10, "Value too high: " + m.getValue());
        }
    }
}
