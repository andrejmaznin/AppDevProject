package maznin.monitoring.api;

import maznin.monitoring.patient.Metric;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Расчёт статистических характеристик измерений средствами PostgreSQL.
 *
 * <p>Вся агрегация выполняется одним SQL-запросом на стороне БД: среднее
 * ({@code avg}), выборочная дисперсия ({@code var_samp}), квартили
 * ({@code percentile_cont(0.25/0.5/0.75) WITHIN GROUP}), минимум, максимум
 * и количество — с группировкой по метрике. В приложение передаются только
 * готовые агрегаты, а не сырые точки.</p>
 *
 * <p>Используется {@code DatabaseClient}, а не репозиторий: оконно-агрегатный
 * SQL с {@code WITHIN GROUP} не выражается средствами Spring Data.
 * Диапазонный скан по {@code measured_at} ускорен BRIN-индексом.</p>
 */
@Service
public class StatisticsService {

    private static final String STATS_SQL = """
            SELECT metric,
                   count(*)                                           AS cnt,
                   avg(value)                                         AS mean,
                   var_samp(value)                                    AS variance,
                   min(value)                                         AS min_value,
                   max(value)                                         AS max_value,
                   percentile_cont(0.25) WITHIN GROUP (ORDER BY value) AS q1,
                   percentile_cont(0.5)  WITHIN GROUP (ORDER BY value) AS median,
                   percentile_cont(0.75) WITHIN GROUP (ORDER BY value) AS q3
            FROM measurements
            WHERE patient_id = :patientId
              AND measured_at >= :from
              AND measured_at < :to
            GROUP BY metric
            ORDER BY metric
            """;

    private final DatabaseClient databaseClient;

    public StatisticsService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Статистика по каждой метрике пациента на полуинтервале
     * {@code [from, to)}. Метрики без измерений на интервале в результат
     * не попадают; дисперсия {@code null} при единственном измерении
     * (поведение {@code var_samp}).
     *
     * @param patientId идентификатор пациента
     * @param from начало интервала (включается)
     * @param to конец интервала (не включается)
     * @return по одному элементу на метрику, отсортировано по ключу метрики
     */
    public Flux<MetricStatistics> getStatistics(UUID patientId, OffsetDateTime from, OffsetDateTime to) {
        return databaseClient.sql(STATS_SQL)
                .bind("patientId", patientId)
                .bind("from", from)
                .bind("to", to)
                .map((row, meta) -> {
                    String metricKey = row.get("metric", String.class);
                    return new MetricStatistics(
                            metricKey,
                            unitOf(metricKey),
                            row.get("cnt", Long.class),
                            toDouble(row.get("mean", BigDecimal.class)),
                            toDouble(row.get("variance", BigDecimal.class)),
                            toDouble(row.get("min_value", BigDecimal.class)),
                            toDouble(row.get("max_value", BigDecimal.class)),
                            row.get("q1", Double.class),
                            row.get("median", Double.class),
                            row.get("q3", Double.class)
                    );
                })
                .all();
    }

    /** {@code NUMERIC}-агрегаты приходят как {@code BigDecimal}; null-безопасно. */
    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /** Единица измерения по ключу метрики; пустая строка для неизвестного ключа. */
    private static String unitOf(String metricKey) {
        for (Metric m : Metric.values()) {
            if (m.getKey().equals(metricKey)) return m.getUnit();
        }
        return "";
    }
}
