package maznin.monitoring.patient;

import maznin.monitoring.api.MetricStatistics;
import maznin.monitoring.api.SenMLMeasurement;
import maznin.monitoring.api.StatisticsService;
import maznin.monitoring.api.StreamingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;
    private final StreamingService streamingService;
    private final StatisticsService statisticsService;
    private final MeasurementRepository measurementRepository;

    public PatientController(PatientService patientService, StreamingService streamingService,
                             StatisticsService statisticsService, MeasurementRepository measurementRepository) {
        this.patientService = patientService;
        this.streamingService = streamingService;
        this.statisticsService = statisticsService;
        this.measurementRepository = measurementRepository;
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

    @GetMapping("/{id}/statistics")
    public Flux<MetricStatistics> getStatistics(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusHours(1);
        return patientService.getPatient(id)
                .thenMany(statisticsService.getStatistics(id, effectiveFrom, effectiveTo));
    }

    @GetMapping("/{id}/measurements")
    public Flux<SenMLMeasurement> getMeasurements(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int limit) {
        return patientService.getPatient(id)
                .thenMany(measurementRepository.findRecentByPatientId(id, limit))
                .map(m -> new SenMLMeasurement(m.getMetric(), unitOf(m.getMetric()), m.getValue(), m.getMeasuredAt()));
    }

    private static String unitOf(String metricKey) {
        for (Metric metric : Metric.values()) {
            if (metric.getKey().equals(metricKey)) return metric.getUnit();
        }
        return "";
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
