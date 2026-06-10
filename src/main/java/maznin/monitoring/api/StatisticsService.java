package maznin.monitoring.api;

import maznin.monitoring.patient.Metric;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aggregates measurement statistics in PostgreSQL (mean, variance, quartiles).
 * The time-range scan over measured_at is accelerated by the BRIN index.
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

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static String unitOf(String metricKey) {
        for (Metric m : Metric.values()) {
            if (m.getKey().equals(metricKey)) return m.getUnit();
        }
        return "";
    }
}
