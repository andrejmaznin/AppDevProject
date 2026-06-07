package maznin.monitoring.engine;

import maznin.monitoring.patient.Measurement;
import maznin.monitoring.patient.MeasurementRepository;
import maznin.monitoring.patient.Metric;
import com.github.f4b6a3.uuid.UuidCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetricGeneratorTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MetricGeneratorTask.class);
    private static final Random random = new Random();

    private final UUID patientId;
    private final Metric metric;
    private final MeasurementRepository measurementRepository;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private double currentValue;
    private final double theta = 0.1; // Mean-reversion rate
    private final double sigma = 0.5; // Volatility

    public MetricGeneratorTask(UUID patientId, Metric metric, MeasurementRepository measurementRepository) {
        this.patientId = patientId;
        this.metric = metric;
        this.measurementRepository = measurementRepository;
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
                // Ornstein-Uhlenbeck Process
                // x(t+1) = x(t) + Theta * (Mu - x(t)) * dt + Sigma * sqrt(dt) * N(0,1)
                double mu = metric.getMu();
                double noise = random.nextGaussian();
                double deltaX = theta * (mu - currentValue) * dt + sigma * Math.sqrt(dt) * noise;
                currentValue += deltaX;

                Measurement measurement = new Measurement(
                        UuidCreator.getTimeOrderedEpoch(),
                        patientId,
                        metric.getKey(),
                        currentValue,
                        OffsetDateTime.now()
                );

                measurementRepository.save(measurement).subscribe();

                Thread.sleep(metric.getTickRateMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Metric generation interrupted for patient {} metric {}", patientId, metric.getKey());
                break;
            } catch (Exception e) {
                logger.error("Error in metric generation for patient {} metric {}", patientId, metric.getKey(), e);
            }
        }
        logger.info("Stopped metric generation for patient {} metric {}", patientId, metric.getKey());
    }
}
