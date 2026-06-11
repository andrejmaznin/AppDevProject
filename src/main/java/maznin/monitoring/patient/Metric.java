package maznin.monitoring.patient;

/**
 * Каталог наблюдаемых витальных показателей и их физиологических параметров.
 *
 * <p>Каждая константа задаёт всё необходимое для генерации и интерпретации
 * метрики: строковый ключ (значение колонки {@code metric} в БД и поля
 * {@code n} в SenML), единицу измерения, базовый уровень μ, границы
 * нормального диапазона, период генерации и волатильность σ.</p>
 *
 * <p>σ масштабирована под ширину нормального диапазона каждой метрики, чтобы
 * значения держались преимущественно в норме, а критические инциденты были
 * редкими событиями, а не шумом (диапазон температуры — всего 0.7&nbsp;°C,
 * поэтому её σ на два порядка меньше пульсовой).</p>
 */
public enum Metric {
    /** Частота пульса: 75 ± норма 60–90 уд/мин, тик 1 с. */
    HEART_RATE("heart_rate", "bpm", 75.0, 60.0, 90.0, 1000, 2.0),
    /** Центральное венозное давление: 5 ± норма 2–8 мм рт. ст., тик 3 с. */
    CVP("cvp", "mmHg", 5.0, 2.0, 8.0, 3000, 0.35),
    /** Температура тела: 36.6 ± норма 36.5–37.2 °C, тик 10 с. */
    TEMPERATURE("temperature", "cel", 36.6, 36.5, 37.2, 10000, 0.04);

    private final String key;
    private final String unit;
    private final double mu;
    private final double rangeMin;
    private final double rangeMax;
    private final long tickRateMs;
    private final double sigma;

    Metric(String key, String unit, double mu, double rangeMin, double rangeMax, long tickRateMs, double sigma) {
        this.key = key;
        this.unit = unit;
        this.mu = mu;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.tickRateMs = tickRateMs;
        this.sigma = sigma;
    }

    /** @return волатильность σ стохастического процесса генерации */
    public double getSigma() {
        return sigma;
    }

    /** @return строковый ключ метрики: значение в БД и поле {@code n} SenML */
    public String getKey() {
        return key;
    }

    /** @return единица измерения (поле {@code u} SenML): bpm, mmHg, cel */
    public String getUnit() {
        return unit;
    }

    /** @return базовый уровень μ — точка притяжения процесса Орнштейна–Уленбека */
    public double getMu() {
        return mu;
    }

    /** @return нижняя граница нормы; значение ниже открывает критический инцидент */
    public double getRangeMin() {
        return rangeMin;
    }

    /** @return верхняя граница нормы; значение выше открывает критический инцидент */
    public double getRangeMax() {
        return rangeMax;
    }

    /** @return период генерации измерений в миллисекундах */
    public long getTickRateMs() {
        return tickRateMs;
    }
}
