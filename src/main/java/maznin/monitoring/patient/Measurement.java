package maznin.monitoring.patient;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("measurements")
public class Measurement {
    @Id
    private UUID id;
    private UUID patientId;
    private String metric;
    private Double value;
    private OffsetDateTime measuredAt;

    public Measurement() {}

    public Measurement(UUID id, UUID patientId, String metric, Double value, OffsetDateTime measuredAt) {
        this.id = id;
        this.patientId = patientId;
        this.metric = metric;
        this.value = value;
        this.measuredAt = measuredAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public OffsetDateTime getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(OffsetDateTime measuredAt) {
        this.measuredAt = measuredAt;
    }
}
