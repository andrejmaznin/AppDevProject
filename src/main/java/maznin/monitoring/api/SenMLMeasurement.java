package maznin.monitoring.api;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Измерение в формате SenML (RFC 8428) — телесный формат SSE-событий
 * {@code metric} и ответа эндпоинта истории измерений.
 *
 * <p>Однобуквенные поля заданы стандартом: {@code n} (name) — ключ метрики,
 * {@code u} (unit) — единица измерения, {@code v} (value) — числовое
 * значение, {@code t} (time) — момент измерения строкой ISO 8601 (UTC).</p>
 */
public class SenMLMeasurement {
    private String n;
    private String u;
    private Double v;
    private String t;

    public SenMLMeasurement() {}

    /**
     * @param n ключ метрики (например {@code heart_rate})
     * @param u единица измерения (например {@code bpm})
     * @param v значение
     * @param t момент измерения; сериализуется как ISO-instant, {@code null} допустим
     */
    public SenMLMeasurement(String n, String u, Double v, OffsetDateTime t) {
        this.n = n;
        this.u = u;
        this.v = v;
        if (t != null) {
            this.t = t.format(DateTimeFormatter.ISO_INSTANT);
        }
    }

    public String getN() {
        return n;
    }

    public void setN(String n) {
        this.n = n;
    }

    public String getU() {
        return u;
    }

    public void setU(String u) {
        this.u = u;
    }

    public Double getV() {
        return v;
    }

    public void setV(Double v) {
        this.v = v;
    }

    public String getT() {
        return t;
    }

    public void setT(String t) {
        this.t = t;
    }
}
