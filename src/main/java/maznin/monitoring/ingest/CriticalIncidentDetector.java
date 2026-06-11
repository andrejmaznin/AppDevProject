package maznin.monitoring.ingest;

import com.github.f4b6a3.uuid.UuidCreator;
import maznin.monitoring.api.IncidentStreamingService;
import maznin.monitoring.patient.CriticalIncident;
import maznin.monitoring.patient.CriticalIncidentRepository;
import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Детектор критических инцидентов — правило предметной области
 * «значение вне нормального диапазона — критический эпизод».
 *
 * <p>Потребляет каждое входящее измерение и ведёт конечный автомат
 * по каждой паре «пациент × метрика»:</p>
 * <ul>
 *   <li><i>вне нормы, эпизода нет</i> — открыть: синхронный INSERT
 *       (гарантия записи в БД до уведомления), затем публикация события;</li>
 *   <li><i>вне нормы, эпизод открыт</i> — обновить пиковое отклонение от μ;</li>
 *   <li><i>в норме, эпизод открыт</i> — закрыть: {@code resolvedAt},
 *       публикация, асинхронный UPDATE;</li>
 *   <li><i>поток метрики завершён</i> ({@link #onStreamClosed}) — закрыть
 *       открытый эпизод, чтобы не оставалось «вечно активных» записей.</li>
 * </ul>
 *
 * <p><b>Потокобезопасность:</b> состояния хранятся в {@code ConcurrentHashMap};
 * по контракту пакета вызовы для одной пары пациент×метрика поступают
 * последовательно, поэтому к отдельному состоянию конкурентного доступа нет.
 * Разные пары обрабатываются независимо из разных потоков.</p>
 */
@Service
public class CriticalIncidentDetector {

    private static final Logger logger = LoggerFactory.getLogger(CriticalIncidentDetector.class);

    private final CriticalIncidentRepository criticalIncidentRepository;
    private final IncidentStreamingService incidentStreamingService;

    /** Открытые эпизоды: "patientId:metricKey" → инцидент. */
    private final Map<String, CriticalIncident> activeIncidents = new ConcurrentHashMap<>();

    public CriticalIncidentDetector(CriticalIncidentRepository criticalIncidentRepository,
                                    IncidentStreamingService incidentStreamingService) {
        this.criticalIncidentRepository = criticalIncidentRepository;
        this.incidentStreamingService = incidentStreamingService;
    }

    /**
     * Обрабатывает одно измерение: сверяет значение с нормой метрики и
     * выполняет переход конечного автомата эпизода. Измерения с неизвестным
     * ключом метрики или без значения игнорируются.
     *
     * @param measurement входящее измерение
     */
    public void onMeasurement(Measurement measurement) {
        Metric metric = Metric.fromKey(measurement.getMetric());
        if (metric == null || measurement.getValue() == null) {
            return;
        }

        String key = stateKey(measurement.getPatientId(), metric.getKey());
        CriticalIncident incident = activeIncidents.get(key);
        double value = measurement.getValue();
        boolean isCritical = value < metric.getRangeMin() || value > metric.getRangeMax();

        if (isCritical) {
            if (incident == null) {
                openIncident(key, measurement);
            } else if (deviationFromMu(metric, value) > deviationFromMu(metric, incident.getMaxDeviationValue())) {
                incident.setMaxDeviationValue(value);
            }
        } else if (incident != null) {
            resolveIncident(key, incident, measurement.getMeasuredAt());
        }
    }

    /**
     * Сигнал «источник перестал поставлять метрику» (мониторинг остановлен,
     * датчик отключён): открытый эпизод закрывается текущим моментом.
     *
     * @param patientId идентификатор пациента
     * @param metricKey строковый ключ метрики
     */
    public void onStreamClosed(UUID patientId, String metricKey) {
        String key = stateKey(patientId, metricKey);
        CriticalIncident incident = activeIncidents.get(key);
        if (incident != null) {
            resolveIncident(key, incident, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    /**
     * Открывает эпизод: INSERT выполняется синхронно — подписчики не должны
     * узнать об инциденте раньше, чем он зафиксирован в БД. Ошибка записи
     * логируется; состояние не создаётся (попытка повторится на следующем
     * критическом измерении).
     */
    private void openIncident(String key, Measurement measurement) {
        CriticalIncident incident = new CriticalIncident(
                UuidCreator.getTimeOrderedEpoch(),
                measurement.getPatientId(),
                measurement.getMetric(),
                measurement.getMeasuredAt(),
                measurement.getValue()
        );
        try {
            CriticalIncident saved = criticalIncidentRepository.save(incident).block();
            if (saved != null) {
                saved.markNotNew(); // следующий save() должен выполнить UPDATE
                activeIncidents.put(key, saved);
                incidentStreamingService.publish(saved);
            }
        } catch (Exception e) {
            logger.error("Failed to save critical incident for patient {} metric {}",
                    measurement.getPatientId(), measurement.getMetric(), e);
        }
    }

    /**
     * Закрывает эпизод: событие публикуется немедленно (момент закрытия уже
     * зафиксирован в объекте), UPDATE уходит асинхронно.
     */
    private void resolveIncident(String key, CriticalIncident incident, OffsetDateTime resolvedAt) {
        incident.setResolvedAt(resolvedAt);
        activeIncidents.remove(key);
        incidentStreamingService.publish(incident);
        criticalIncidentRepository.save(incident)
                .subscribe(
                        saved -> {},
                        e -> logger.error("Failed to resolve critical incident {}", incident.getId(), e)
                );
    }

    /** Абсолютное отклонение значения от базового уровня μ метрики. */
    private static double deviationFromMu(Metric metric, Double value) {
        return value == null ? 0.0 : Math.abs(value - metric.getMu());
    }

    private static String stateKey(UUID patientId, String metricKey) {
        return patientId + ":" + metricKey;
    }
}
