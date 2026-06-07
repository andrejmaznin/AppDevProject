package maznin.monitoring.engine;

import maznin.monitoring.patient.MeasurementRepository;
import maznin.monitoring.patient.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MetricGenerationEngine {
    private static final Logger logger = LoggerFactory.getLogger(MetricGenerationEngine.class);

    private final MeasurementRepository measurementRepository;
    private final ExecutorService executorService;
    private final Map<String, MetricGeneratorTask> tasks = new ConcurrentHashMap<>();

    public MetricGenerationEngine(MeasurementRepository measurementRepository) {
        this.measurementRepository = measurementRepository;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void startMonitoring(UUID patientId) {
        for (Metric metric : Metric.values()) {
            String taskKey = getTaskKey(patientId, metric);
            if (!tasks.containsKey(taskKey)) {
                MetricGeneratorTask task = new MetricGeneratorTask(patientId, metric, measurementRepository);
                tasks.put(taskKey, task);
                executorService.submit(task);
                logger.info("Monitoring started for patient {} metric {}", patientId, metric.getKey());
            }
        }
    }

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

    private String getTaskKey(UUID patientId, Metric metric) {
        return patientId.toString() + ":" + metric.getKey();
    }
}
