package maznin.monitoring.api;

import maznin.monitoring.patient.CriticalIncident;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;

@Service
public class IncidentStreamingService {

    private final Sinks.Many<CriticalIncident> sink = Sinks.many().multicast().directBestEffort();

    public void publish(CriticalIncident incident) {
        sink.tryEmitNext(incident);
    }

    public Flux<CriticalIncident> getStream(UUID patientId) {
        return sink.asFlux()
                .filter(i -> patientId.equals(i.getPatientId()));
    }
}
