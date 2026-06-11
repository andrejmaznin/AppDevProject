package maznin.monitoring.patient;

/**
 * DTO запроса регистрации пациента: имя и фамилия.
 * Идентификатор и статус мониторинга клиентом не задаются —
 * их присваивает сервер.
 */
public class PatientRequest {
    private String firstName;
    private String lastName;

    public PatientRequest() {}

    public PatientRequest(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
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
}
