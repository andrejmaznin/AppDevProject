package maznin.monitoring.engine;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Процесс Орнштейна–Уленбека: x(t+1) = x(t) + Θ·(μ − x(t))·dt + σ·√dt·N(0,1).
 * Значение колеблется вокруг μ, σ масштабирована под ширину нормального
 * диапазона конкретной метрики.
 */
public class OrnsteinUhlenbeckGenerator implements ValueGenerator {

    private final double theta;
    private final double mu;
    private final double sigma;

    public OrnsteinUhlenbeckGenerator(double theta, double mu, double sigma) {
        this.theta = theta;
        this.mu = mu;
        this.sigma = sigma;
    }

    @Override
    public double next(double currentValue, double dtSeconds) {
        double noise = ThreadLocalRandom.current().nextGaussian();
        return currentValue + theta * (mu - currentValue) * dtSeconds
                + sigma * Math.sqrt(dtSeconds) * noise;
    }
}
