package maznin.monitoring.patient;

import maznin.monitoring.api.SenMLMeasurement;
import maznin.monitoring.api.StreamingService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;
    private final StreamingService streamingService;

    public PatientController(PatientService patientService, StreamingService streamingService) {
        this.patientService = patientService;
        this.streamingService = streamingService;
    }

    @PostMapping
    public Mono<Patient> registerPatient(@RequestBody PatientRequest request) {
        return patientService.registerPatient(request);
    }

    @GetMapping
    public Flux<Patient> getAllPatients() {
        return patientService.getAllPatients();
    }

    @GetMapping("/{id}")
    public Mono<Patient> getPatient(@PathVariable UUID id) {
        return patientService.getPatient(id);
    }

    @PostMapping("/{id}/monitoring/start")
    public Mono<Void> startMonitoring(@PathVariable UUID id) {
        return patientService.startMonitoring(id);
    }

    @PostMapping("/{id}/monitoring/stop")
    public Mono<Void> stopMonitoring(@PathVariable UUID id) {
        return patientService.stopMonitoring(id);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SenMLMeasurement>> getStream(@PathVariable UUID id) {
        return streamingService.getStream(id)
                .map(measurement -> ServerSentEvent.<SenMLMeasurement>builder()
                        .event("metric")
                        .data(measurement)
                        .build());
    }
}
