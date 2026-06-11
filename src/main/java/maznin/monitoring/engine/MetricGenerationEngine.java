package maznin.monitoring.engine;

import maznin.monitoring.ingest.MeasurementIngestService;
import maznin.monitoring.patient.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Инициатор задач генерации телеметрии (паттерн Команда).
 *
 * <p>Ведёт реестр активных {@link MetricGeneratorTask} с ключом
 * «patientId:metric» и исполняет их на виртуальных потоках
 * ({@code Executors.newVirtualThreadPerTaskExecutor()}): тысячи одновременно
 * наблюдаемых пациентов не требуют пула потоков ОС.</p>
 *
 * <p>Идемпотентность: повторный {@code startMonitoring} для уже наблюдаемого
 * пациента не создаёт дублирующих задач, повторный {@code stopMonitoring} —
 * безвреден.</p>
 */
@Service
public class MetricGenerationEngine {
    private static final Logger logger = LoggerFactory.getLogger(MetricGenerationEngine.class);

    private final MeasurementIngestService ingestService;
    private final ExecutorService executorService;
    private final Map<String, MetricGeneratorTask> tasks = new ConcurrentHashMap<>();

    /**
     * @param ingestService точка приёма измерений, передаётся задачам —
     *        единственная связь эмулятора с системой мониторинга
     */
    public MetricGenerationEngine(MeasurementIngestService ingestService) {
        this.ingestService = ingestService;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Запускает генерацию всех метрик ({@link Metric#values()}) для пациента:
     * по одной задаче на метрику, каждая на собственном виртуальном потоке.
     * Уже работающие задачи не дублируются.
     *
     * @param patientId идентификатор пациента
     */
    public void startMonitoring(UUID patientId) {
        for (Metric metric : Metric.values()) {
            String taskKey = getTaskKey(patientId, metric);
            if (!tasks.containsKey(taskKey)) {
                MetricGeneratorTask task = new MetricGeneratorTask(patientId, metric, ingestService);
                tasks.put(taskKey, task);
                executorService.submit(task);
                logger.info("Monitoring started for patient {} metric {}", patientId, metric.getKey());
            }
        }
    }

    /**
     * Останавливает все задачи генерации пациента и удаляет их из реестра.
     * Каждая задача завершает текущий тик и сигнализирует {@code streamClosed} —
     * открытые критические эпизоды будут закрыты детектором. Отсутствие
     * задач — не ошибка.
     *
     * @param patientId идентификатор пациента
     */
    public void stopMonitoring(UUID patientId) {
        for (Metric metric : Metric.values()) {
            String taskKey = getTaskKey(patientId, metric);
            MetricGeneratorTask task = tasks.remove(taskKey);
            if (task != null) {
                task.stop();
                logger.info("Monitoring stopped for patient {} metric {}", patientId, metric.getKey());
            }
        }
    }

    /**
     * Ключ задачи в реестре: {@code "<patientId>:<metricKey>"}.
     */
    private String getTaskKey(UUID patientId, Metric metric) {
        return patientId.toString() + ":" + metric.getKey();
    }
}
