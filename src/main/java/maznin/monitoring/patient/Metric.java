package maznin.monitoring.patient;

public enum Metric {
    HEART_RATE("heart_rate", "bpm", 75.0, 60.0, 90.0, 1000),
    CVP("cvp", "mmHg", 5.0, 2.0, 8.0, 3000),
    TEMPERATURE("temperature", "cel", 36.6, 36.5, 37.2, 10000);

    private final String key;
    private final String unit;
    private final double mu;
    private final double rangeMin;
    private final double rangeMax;
    private final long tickRateMs;

    Metric(String key, String unit, double mu, double rangeMin, double rangeMax, long tickRateMs) {
        this.key = key;
        this.unit = unit;
        this.mu = mu;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.tickRateMs = tickRateMs;
    }

    public String getKey() {
        return key;
    }

    public String getUnit() {
        return unit;
    }

    public double getMu() {
        return mu;
    }

    public double getRangeMin() {
        return rangeMin;
    }

    public double getRangeMax() {
        return rangeMax;
    }

    public long getTickRateMs() {
        return tickRateMs;
    }
}
