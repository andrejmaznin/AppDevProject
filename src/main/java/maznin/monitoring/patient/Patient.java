package maznin.monitoring.patient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("patients")
public class Patient implements Persistable<UUID> {
    @Id
    private UUID id;
    private String firstName;
    private String lastName;
    private boolean isMonitoringActive;

    // IDs are assigned in code (UUIDv7), so Spring Data can't infer new vs existing;
    // without this flag save() would issue an UPDATE for never-persisted rows.
    @Transient
    private boolean newEntity = false;

    public Patient() {}

    public Patient(UUID id, String firstName, String lastName, boolean isMonitoringActive) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isMonitoringActive = isMonitoringActive;
        this.newEntity = true;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return newEntity;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isMonitoringActive() {
        return isMonitoringActive;
    }

    public void setMonitoringActive(boolean monitoringActive) {
        isMonitoringActive = monitoringActive;
    }
}
