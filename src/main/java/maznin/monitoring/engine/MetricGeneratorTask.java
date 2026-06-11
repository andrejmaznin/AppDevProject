package maznin.monitoring.engine;

import maznin.monitoring.api.IncidentStreamingService;
import maznin.monitoring.patient.CriticalIncident;
import maznin.monitoring.patient.CriticalIncidentRepository;
import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.MeasurementRepository;
import maznin.monitoring.patient.Metric;
import com.github.f4b6a3.uuid.UuidCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Команда генерации телеметрии одной метрики одного пациента.
 *
 * <p>Выполняется на выделенном виртуальном потоке. На каждом тике
 * (период {@link Metric#getTickRateMs()}):</p>
 * <ol>
 *   <li>генерирует следующее значение через стратегию {@link ValueGenerator};</li>
 *   <li>сохраняет {@link Measurement} в БД (асинхронно, fire-and-forget —
 *       SSE-доставка произойдёт через триггер PostgreSQL);</li>
 *   <li>сверяет значение с нормальным диапазоном метрики и ведёт конечный
 *       автомат критического инцидента (см. {@link #trackCriticalIncident}).</li>
 * </ol>
 *
 * <p><b>Потокобезопасность:</b> всё мутабельное состояние ({@code currentValue},
 * {@code activeIncident}) принадлежит исключительно потоку задачи; извне
 * допустим только вызов {@link #stop()} через атомарный флаг.</p>
 */
public class MetricGeneratorTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MetricGeneratorTask.class);

    private final UUID patientId;
    private final Metric metric;
    private final MeasurementRepository measurementRepository;
    private final CriticalIncidentRepository criticalIncidentRepository;
    private final IncidentStreamingService incidentStreamingService;
    private final ValueGenerator valueGenerator;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private double currentValue;

    // Active incident tracking (single-threaded — only this virtual thread accesses these)
    private CriticalIncident activeIncident = null;

    /**
     * Упрощённый конструктор без публикации событий инцидентов.
     *
     * @param patientId пациент, для которого генерируется метрика
     * @param metric генерируемая метрика (задаёт μ, σ, норму и период тика)
     * @param measurementRepository репозиторий для сохранения измерений
     * @param criticalIncidentRepository репозиторий инцидентов; {@code null}
     *        полностью отключает отслеживание инцидентов
     */
    public MetricGeneratorTask(UUID patientId, Metric metric,
                               MeasurementRepository measurementRepository,
                               CriticalIncidentRepository criticalIncidentRepository) {
        this(patientId, metric, measurementRepository, criticalIncidentRepository, null);
    }

    /**
     * Основной конструктор: стратегия — процесс Орнштейна–Уленбека
     * с параметрами метрики (Θ = 0.1, μ и σ из {@link Metric}).
     *
     * @param incidentStreamingService издатель событий инцидентов для
     *        SSE-подписчиков; {@code null} — события не публикуются
     */
    public MetricGeneratorTask(UUID patientId, Metric metric,
                               MeasurementRepository measurementRepository,
                               CriticalIncidentRepository criticalIncidentRepository,
                               IncidentStreamingService incidentStreamingService) {
        this(patientId, metric, measurementRepository, criticalIncidentRepository, incidentStreamingService,
                new OrnsteinUhlenbeckGenerator(0.1, metric.getMu(), metric.getSigma()));
    }

    /**
     * Полный конструктор с явной стратегией генерации — в тестах подменяется
     * детерминированной последовательностью значений.
     *
     * @param valueGenerator стратегия вычисления следующего значения метрики
     */
    public MetricGeneratorTask(UUID patientId, Metric metric,
                               MeasurementRepository measurementRepository,
                               CriticalIncidentRepository criticalIncidentRepository,
                               IncidentStreamingService incidentStreamingService,
                               ValueGenerator valueGenerator) {
        this.patientId = patientId;
        this.metric = metric;
        this.measurementRepository = measurementRepository;
        this.criticalIncidentRepository = criticalIncidentRepository;
        this.incidentStreamingService = incidentStreamingService;
        this.valueGenerator = valueGenerator;
        this.currentValue = metric.getMu();
    }

    /**
     * Запрашивает останов задачи. Цикл завершится после текущего тика;
     * открытый инцидент при этом будет закрыт (см. конец {@link #run()}).
     * Безопасно вызывается из любого потока.
     */
    public void stop() {
        running.set(false);
    }

    /**
     * Основной цикл генерации: значение → сохранение → проверка границ → сон.
     * Ошибки отдельного тика логируются и не прерывают цикл; прерывание потока
     * ({@code InterruptedException}) завершает задачу. На выходе закрывает
     * незакрытый инцидент, чтобы в БД не оставалось «вечно активных» записей.
     */
    @Override
    public void run() {
        logger.info("Starting metric generation for patient {} metric {}", patientId, metric.getKey());
        double dt = metric.getTickRateMs() / 1000.0;

        while (running.get()) {
            try {
                currentValue = valueGenerator.next(currentValue, dt);

                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                Measurement measurement = new Measurement(
                        UuidCreator.getTimeOrderedEpoch(),
                        patientId,
                        metric.getKey(),
                        currentValue,
                        now
                );
                measurementRepository.save(measurement).subscribe();

                trackCriticalIncident(now);

                Thread.sleep(metric.getTickRateMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Metric generation interrupted for patient {} metric {}", patientId, metric.getKey());
                break;
            } catch (Exception e) {
                logger.error("Error in metric generation for patient {} metric {}", patientId, metric.getKey(), e);
            }
        }

        // Resolve any open incident when monitoring is stopped
        if (activeIncident != null) {
            resolveIncident(OffsetDateTime.now(ZoneOffset.UTC));
        }

        logger.info("Stopped metric generation for patient {} metric {}", patientId, metric.getKey());
    }

    /**
     * Конечный автомат критического инцидента.
     *
     * <p>Переходы по текущему значению метрики:</p>
     * <ul>
     *   <li><i>вне нормы, инцидента нет</i> — открыть: синхронный INSERT
     *       (block — гарантия записи до уведомления подписчиков), затем
     *       публикация события;</li>
     *   <li><i>вне нормы, инцидент открыт</i> — обновить пиковое отклонение,
     *       если текущее значение дальше от μ, чем зафиксированное;</li>
     *   <li><i>в норме, инцидент открыт</i> — закрыть через
     *       {@link #resolveIncident}.</li>
     * </ul>
     *
     * @param now момент измерения, становится {@code startedAt} нового инцидента
     */
    private void trackCriticalIncident(OffsetDateTime now) {
        if (criticalIncidentRepository == null) return;

        boolean isCritical = currentValue < metric.getRangeMin() || currentValue > metric.getRangeMax();

        if (isCritical) {
            if (activeIncident == null) {
                CriticalIncident incident = new CriticalIncident(
                        UuidCreator.getTimeOrderedEpoch(),
                        patientId,
                        metric.getKey(),
                        now,
                        currentValue
                );
                try {
                    activeIncident = criticalIncidentRepository.save(incident).block();
                    if (activeIncident != null) {
                        activeIncident.markNotNew(); // next save() must UPDATE
                        if (incidentStreamingService != null) {
                            incidentStreamingService.publish(activeIncident);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to save critical incident for patient {} metric {}", patientId, metric.getKey(), e);
                }
            } else {
                // Update max deviation: keep the value farthest from baseline Mu
                if (deviationFrom(currentValue) > deviationFrom(activeIncident.getMaxDeviationValue())) {
                    activeIncident.setMaxDeviationValue(currentValue);
                }
            }
        } else if (activeIncident != null) {
            resolveIncident(now);
        }
    }

    /**
     * Закрывает активный инцидент: проставляет {@code resolvedAt}, немедленно
     * публикует событие подписчикам и асинхронно сохраняет UPDATE в БД
     * (доставка уведомления не ждёт записи — момент закрытия уже зафиксирован
     * в объекте).
     *
     * @param resolvedAt момент возврата значения в норму или останова мониторинга
     */
    private void resolveIncident(OffsetDateTime resolvedAt) {
        activeIncident.setResolvedAt(resolvedAt);
        if (incidentStreamingService != null) {
            incidentStreamingService.publish(activeIncident);
        }
        final CriticalIncident toSave = activeIncident;
        criticalIncidentRepository.save(toSave)
                .subscribe(
                        saved -> {},
                        e -> logger.error("Failed to resolve critical incident {}", toSave.getId(), e)
                );
        activeIncident = null;
    }

    /**
     * Абсолютное отклонение значения от базового уровня μ метрики.
     * Используется для сравнения «какое значение экстремальнее» независимо
     * от направления выхода (выше или ниже нормы).
     *
     * @param value сравниваемое значение; {@code null} трактуется как нулевое отклонение
     * @return {@code |value − μ|}
     */
    private double deviationFrom(Double value) {
        if (value == null) return 0.0;
        return Math.abs(value - metric.getMu());
    }
}
