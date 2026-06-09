package maznin.monitoring.patient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("critical_incidents")
public class CriticalIncident implements Persistable<UUID> {
    @Id
    private UUID id;
    private UUID patientId;
    private String metric;
    private OffsetDateTime startedAt;
    private OffsetDateTime resolvedAt;
    private Double maxDeviationValue;

    // IDs are assigned in code (UUIDv7); incident is INSERTed once, then UPDATEd on resolve
    @Transient
    private boolean newEntity = false;

    public CriticalIncident() {}

    public CriticalIncident(UUID id, UUID patientId, String metric, OffsetDateTime startedAt, Double maxDeviationValue) {
        this.id = id;
        this.patientId = patientId;
        this.metric = metric;
        this.startedAt = startedAt;
        this.maxDeviationValue = maxDeviationValue;
        this.newEntity = true;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return newEntity;
    }

    public void markNotNew() {
        this.newEntity = false;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public Double getMaxDeviationValue() { return maxDeviationValue; }
    public void setMaxDeviationValue(Double maxDeviationValue) { this.maxDeviationValue = maxDeviationValue; }
}
