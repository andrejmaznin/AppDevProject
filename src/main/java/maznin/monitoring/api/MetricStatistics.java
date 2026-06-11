package maznin.monitoring.api;

/**
 * DTO статистических характеристик одной метрики на интервале времени.
 *
 * <p>Поля: {@code metric} — ключ метрики, {@code unit} — единица измерения,
 * {@code count} — число измерений (N), {@code mean} — среднее,
 * {@code variance} — выборочная дисперсия ({@code null} при N = 1),
 * {@code min}/{@code max} — экстремумы, {@code q1}/{@code median}/{@code q3} —
 * квартили (интерполированные, {@code percentile_cont}).</p>
 */
public class MetricStatistics {
    private String metric;
    private String unit;
    private long count;
    private Double mean;
    private Double variance;
    private Double min;
    private Double max;
    private Double q1;
    private Double median;
    private Double q3;

    public MetricStatistics() {}

    public MetricStatistics(String metric, String unit, long count, Double mean, Double variance,
                            Double min, Double max, Double q1, Double median, Double q3) {
        this.metric = metric;
        this.unit = unit;
        this.count = count;
        this.mean = mean;
        this.variance = variance;
        this.min = min;
        this.max = max;
        this.q1 = q1;
        this.median = median;
        this.q3 = q3;
    }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public Double getMean() { return mean; }
    public void setMean(Double mean) { this.mean = mean; }

    public Double getVariance() { return variance; }
    public void setVariance(Double variance) { this.variance = variance; }

    public Double getMin() { return min; }
    public void setMin(Double min) { this.min = min; }

    public Double getMax() { return max; }
    public void setMax(Double max) { this.max = max; }

    public Double getQ1() { return q1; }
    public void setQ1(Double q1) { this.q1 = q1; }

    public Double getMedian() { return median; }
    public void setMedian(Double median) { this.median = median; }

    public Double getQ3() { return q3; }
    public void setQ3(Double q3) { this.q3 = q3; }
}
