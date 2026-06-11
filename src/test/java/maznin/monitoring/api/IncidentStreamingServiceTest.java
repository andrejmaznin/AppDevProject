package maznin.monitoring.api;

import maznin.monitoring.patient.CriticalIncident;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentStreamingServiceTest {

    private static CriticalIncident incident(UUID patientId) {
        return new CriticalIncident(UUID.randomUUID(), patientId, "heart_rate",
                OffsetDateTime.now(), 120.0);
    }

    @Test
    void subscriberReceivesIncidentsForItsPatient() {
        IncidentStreamingService service = new IncidentStreamingService();
        UUID patientId = UUID.randomUUID();
        CriticalIncident published = incident(patientId);

        StepVerifier.create(service.getStream(patientId).take(1))
                .then(() -> service.publish(published))
                .assertNext(received -> assertEquals(published.getId(), received.getId()))
                .verifyComplete();
    }

    @Test
    void incidentsOfOtherPatientsAreFilteredOut() {
        IncidentStreamingService service = new IncidentStreamingService();
        UUID subscribedPatient = UUID.randomUUID();
        UUID otherPatient = UUID.randomUUID();
        CriticalIncident own = incident(subscribedPatient);

        StepVerifier.create(service.getStream(subscribedPatient).take(1))
                .then(() -> service.publish(incident(otherPatient)))
                .then(() -> service.publish(own))
                .assertNext(received -> assertEquals(own.getId(), received.getId()))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}
