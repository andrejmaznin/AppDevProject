package maznin.monitoring.engine;

import com.github.f4b6a3.uuid.UuidCreator;
import maznin.monitoring.ingest.MeasurementIngestService;
import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Команда генерации телеметрии одной метрики одного пациента — чистый
 * эмулятор датчика.
 *
 * <p>Выполняется на выделенном виртуальном потоке. На каждом тике
 * (период {@link Metric#getTickRateMs()}) генерирует следующее значение
 * через стратегию {@link ValueGenerator} и отдаёт измерение в
 * {@link MeasurementIngestService} — дальнейшая судьба данных (сохранение,
 * детекция инцидентов, доставка подписчикам) задачу не касается. При
 * завершении сигнализирует {@code streamClosed}, как это сделал бы
 * отключившийся датчик.</p>
 *
 * <p><b>Потокобезопасность:</b> {@code currentValue} принадлежит
 * исключительно потоку задачи; извне допустим только вызов {@link #stop()}
 * через атомарный флаг.</p>
 */
public class MetricGeneratorTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MetricGeneratorTask.class);

    private final UUID patientId;
    private final Metric metric;
    private final MeasurementIngestService ingestService;
    private final ValueGenerator valueGenerator;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private double currentValue;

    /**
     * Основной конструктор: стратегия — процесс Орнштейна–Уленбека
     * с параметрами метрики (Θ = 0.1, μ и σ из {@link Metric}).
     *
     * @param patientId пациент, для которого генерируется метрика
     * @param metric генерируемая метрика (задаёт μ, σ, норму и период тика)
     * @param ingestService точка приёма сгенерированных измерений
     */
    public MetricGeneratorTask(UUID patientId, Metric metric, MeasurementIngestService ingestService) {
        this(patientId, metric, ingestService,
                new OrnsteinUhlenbeckGenerator(0.1, metric.getMu(), metric.getSigma()));
    }

    /**
     * Конструктор с явной стратегией генерации — в тестах подменяется
     * детерминированной последовательностью значений.
     *
     * @param valueGenerator стратегия вычисления следующего значения метрики
     */
    public MetricGeneratorTask(UUID patientId, Metric metric,
                               MeasurementIngestService ingestService,
                               ValueGenerator valueGenerator) {
        this.patientId = patientId;
        this.metric = metric;
        this.ingestService = ingestService;
        this.valueGenerator = valueGenerator;
        this.currentValue = metric.getMu();
    }

    /**
     * Запрашивает останов задачи. Цикл завершится после текущего тика,
     * после чего источник сигнализирует о закрытии потока метрики.
     * Безопасно вызывается из любого потока.
     */
    public void stop() {
        running.set(false);
    }

    /**
     * Основной цикл эмуляции: значение → приём ({@code ingest}) → сон.
     * Ошибки отдельного тика логируются и не прерывают цикл; прерывание
     * потока завершает задачу. На выходе отправляет {@code streamClosed} —
     * система мониторинга закроет открытый критический эпизод.
     */
    @Override
    public void run() {
        logger.info("Starting metric generation for patient {} metric {}", patientId, metric.getKey());
        double dt = metric.getTickRateMs() / 1000.0;

        while (running.get()) {
            try {
                currentValue = valueGenerator.next(currentValue, dt);

                Measurement measurement = new Measurement(
                        UuidCreator.getTimeOrderedEpoch(),
                        patientId,
                        metric.getKey(),
                        currentValue,
                        OffsetDateTime.now(ZoneOffset.UTC)
                );
                ingestService.ingest(measurement);

                Thread.sleep(metric.getTickRateMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Metric generation interrupted for patient {} metric {}", patientId, metric.getKey());
                break;
            } catch (Exception e) {
                logger.error("Error in metric generation for patient {} metric {}", patientId, metric.getKey(), e);
            }
        }

        ingestService.streamClosed(patientId, metric.getKey());
        logger.info("Stopped metric generation for patient {} metric {}", patientId, metric.getKey());
    }
}
