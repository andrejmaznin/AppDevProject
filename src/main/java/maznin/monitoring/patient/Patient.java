package maznin.monitoring.patient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Пациент — субъект наблюдения.
 *
 * <p>Поле {@code isMonitoringActive} — персистентный источник истины о том,
 * должен ли идти мониторинг: по нему восстанавливаются задачи генерации
 * после перезапуска приложения.</p>
 *
 * <p>Реализует {@code Persistable}: идентификатор UUIDv7 присваивается в
 * коде, поэтому новизна записи определяется {@code @Transient}-флагом,
 * а не пустотой {@code @Id} (см. документацию пакета).</p>
 */
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

    /** Для маппинга строк БД (объект считается уже существующим). */
    public Patient() {}

    /**
     * Создаёт нового пациента; объект помечается новым — первый
     * {@code save()} выполнит INSERT.
     */
    public Patient(UUID id, String firstName, String lastName, boolean isMonitoringActive) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isMonitoringActive = isMonitoringActive;
        this.newEntity = true;
    }

    /**
     * Признак «ещё не сохранён» для Spring Data R2DBC (INSERT vs UPDATE).
     * Скрыт из JSON — служебное поле, а не атрибут предметной области.
     */
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
