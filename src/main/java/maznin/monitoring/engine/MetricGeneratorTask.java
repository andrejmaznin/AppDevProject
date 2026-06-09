package maznin.monitoring.engine;

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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetricGeneratorTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MetricGeneratorTask.class);

    private final UUID patientId;
    private final Metric metric;
    private final MeasurementRepository measurementRepository;
    private final CriticalIncidentRepository criticalIncidentRepository;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private double currentValue;
    private final double theta = 0.1;

    // Active incident tracking (single-threaded — only this virtual thread accesses these)
    private CriticalIncident activeIncident = null;

    public MetricGeneratorTask(UUID patientId, Metric metric,
                               MeasurementRepository measurementRepository,
                               CriticalIncidentRepository criticalIncidentRepository) {
        this.patientId = patientId;
        this.metric = metric;
        this.measurementRepository = measurementRepository;
        this.criticalIncidentRepository = criticalIncidentRepository;
        this.currentValue = metric.getMu();
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        logger.info("Starting metric generation for patient {} metric {}", patientId, metric.getKey());
        double dt = metric.getTickRateMs() / 1000.0;

        while (running.get()) {
            try {
                // Ornstein-Uhlenbeck: x(t+1) = x(t) + Theta*(Mu - x(t))*dt + Sigma*sqrt(dt)*N(0,1)
                double mu = metric.getMu();
                double noise = ThreadLocalRandom.current().nextGaussian();
                double deltaX = theta * (mu - currentValue) * dt + metric.getSigma() * Math.sqrt(dt) * noise;
                currentValue += deltaX;

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
                    if (activeIncident != null) activeIncident.markNotNew(); // next save() must UPDATE
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

    private void resolveIncident(OffsetDateTime resolvedAt) {
        activeIncident.setResolvedAt(resolvedAt);
        criticalIncidentRepository.save(activeIncident)
                .subscribe(
                        saved -> {},
                        e -> logger.error("Failed to resolve critical incident {}", activeIncident.getId(), e)
                );
        activeIncident = null;
    }

    private double deviationFrom(Double value) {
        if (value == null) return 0.0;
        return Math.abs(value - metric.getMu());
    }
}
