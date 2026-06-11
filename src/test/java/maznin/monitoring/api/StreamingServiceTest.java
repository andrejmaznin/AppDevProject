package maznin.monitoring.api;

import maznin.monitoring.patient.Measurement;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingServiceTest {

    // init() не вызывается — LISTEN/NOTIFY не нужен, проверяем только sink → SenML
    private final StreamingService service = new StreamingService(null);

    private static Measurement measurement(UUID patientId, String metric, double value) {
        return new Measurement(UUID.randomUUID(), patientId, metric, value,
                OffsetDateTime.parse("2026-06-10T10:00:00Z").withOffsetSameInstant(ZoneOffset.UTC));
    }

    @Test
    void mapsMeasurementToSenMLWithUnit() {
        UUID patientId = UUID.randomUUID();

        StepVerifier.create(service.getStream(patientId).take(1))
                .then(() -> service.emitForTest(measurement(patientId, "heart_rate", 75.5)))
                .assertNext(senml -> {
                    assertEquals("heart_rate", senml.getN());
                    assertEquals("bpm", senml.getU());
                    assertEquals(75.5, senml.getV());
                    assertEquals("2026-06-10T10:00:00Z", senml.getT());
                })
                .verifyComplete();
    }

    @Test
    void filtersOutMeasurementsOfOtherPatients() {
        UUID subscribedPatient = UUID.randomUUID();
        UUID otherPatient = UUID.randomUUID();

        StepVerifier.create(service.getStream(subscribedPatient).take(1))
                .then(() -> service.emitForTest(measurement(otherPatient, "cvp", 5.0)))
                .then(() -> service.emitForTest(measurement(subscribedPatient, "temperature", 36.7)))
                .assertNext(senml -> {
                    assertEquals("temperature", senml.getN());
                    assertEquals("cel", senml.getU());
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}
