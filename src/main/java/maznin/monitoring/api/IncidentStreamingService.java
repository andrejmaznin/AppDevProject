package maznin.monitoring.api;

import maznin.monitoring.patient.CriticalIncident;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;

/**
 * Издатель событий критических инцидентов (паттерн Наблюдатель).
 *
 * <p>В отличие от {@link StreamingService}, события публикуются напрямую
 * задачами генерации (без посредничества БД): инцидент — редкое и важное
 * событие, его доставка должна быть мгновенной. Каждый инцидент эмитится
 * дважды — при открытии (без {@code resolvedAt}) и при закрытии.</p>
 *
 * <p>Sink — {@code multicast().directBestEffort()}: события доставляются
 * только текущим подписчикам, без буферизации истории (история доступна
 * через REST-эндпоинт инцидентов).</p>
 */
@Service
public class IncidentStreamingService {

    private final Sinks.Many<CriticalIncident> sink = Sinks.many().multicast().directBestEffort();

    /**
     * Публикует событие инцидента всем текущим подписчикам.
     * Вызывается задачей генерации при открытии и закрытии инцидента.
     *
     * @param incident инцидент в текущем состоянии (открыт или закрыт)
     */
    public void publish(CriticalIncident incident) {
        sink.tryEmitNext(incident);
    }

    /**
     * Поток событий инцидентов одного пациента; завершается только
     * отпиской клиента.
     *
     * @param patientId идентификатор пациента
     * @return бесконечный поток событий инцидентов пациента
     */
    public Flux<CriticalIncident> getStream(UUID patientId) {
        return sink.asFlux()
                .filter(i -> patientId.equals(i.getPatientId()));
    }
}
