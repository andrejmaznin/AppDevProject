package maznin.monitoring.patient;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("patients")
public class Patient {
    @Id
    private UUID id;
    private String firstName;
    private String lastName;
    private boolean isMonitoringActive;

    public Patient() {}

    public Patient(UUID id, String firstName, String lastName, boolean isMonitoringActive) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isMonitoringActive = isMonitoringActive;
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
