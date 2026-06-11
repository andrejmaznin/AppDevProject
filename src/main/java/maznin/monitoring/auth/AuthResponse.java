package maznin.monitoring.auth;

/**
 * DTO ответа аутентификации: подписанный JWT для заголовка
 * {@code Authorization: Bearer <token>} (RFC 6750).
 */
public class AuthResponse {
    private String token;

    public AuthResponse() {}

    public AuthResponse(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
