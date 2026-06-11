package maznin.monitoring.ingest;

import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.MeasurementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Фасад приёма измерений — единственная точка входа данных в систему
 * мониторинга.
 *
 * <p>«Измерение вошло в систему» означает две вещи, и обе происходят здесь:
 * сохранение в БД (откуда оно через триггер pg_notify попадёт в SSE-поток)
 * и проверка детектором критических инцидентов. Продюсеру — сегодня это
 * {@code MetricGeneratorTask}, в перспективе адаптер больничной шины —
 * не нужно знать об этих деталях.</p>
 */
@Service
public class MeasurementIngestService {

    private static final Logger logger = LoggerFactory.getLogger(MeasurementIngestService.class);

    private final MeasurementRepository measurementRepository;
    private final CriticalIncidentDetector incidentDetector;

    public MeasurementIngestService(MeasurementRepository measurementRepository,
                                    CriticalIncidentDetector incidentDetector) {
        this.measurementRepository = measurementRepository;
        this.incidentDetector = incidentDetector;
    }

    /**
     * Принимает одно измерение: асинхронно сохраняет в БД и синхронно
     * прогоняет через детектор инцидентов. Ошибка записи логируется и не
     * прерывает продюсера.
     *
     * @param measurement измерение с заполненными пациентом, метрикой,
     *        значением и временем
     */
    public void ingest(Measurement measurement) {
        measurementRepository.save(measurement)
                .subscribe(
                        saved -> {},
                        e -> logger.error("Failed to save measurement for patient {} metric {}",
                                measurement.getPatientId(), measurement.getMetric(), e)
                );
        incidentDetector.onMeasurement(measurement);
    }

    /**
     * Сигнал о завершении потока метрики (мониторинг остановлен, источник
     * отключился): детектор закрывает открытый критический эпизод.
     *
     * @param patientId идентификатор пациента
     * @param metricKey строковый ключ метрики
     */
    public void streamClosed(UUID patientId, String metricKey) {
        incidentDetector.onStreamClosed(patientId, metricKey);
    }
}
