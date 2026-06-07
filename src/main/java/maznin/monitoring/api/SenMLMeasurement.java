package maznin.monitoring.api;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class SenMLMeasurement {
    private String n;
    private String u;
    private Double v;
    private String t;

    public SenMLMeasurement() {}

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
