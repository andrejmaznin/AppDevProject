package maznin.monitoring.engine;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Процесс Орнштейна–Уленбека: <i>x(t+1) = x(t) + Θ·(μ − x(t))·dt + σ·√dt·N(0,1)</i>.
 *
 * <p>Стохастический процесс с возвратом к среднему — стандартная модель
 * физиологической телеметрии: значение случайно блуждает, но детерминированно
 * притягивается к базовому уровню μ с силой Θ. Случайные всплески уводят его
 * за границы нормы (порождая критические инциденты), после чего оно
 * естественно возвращается.</p>
 *
 * <p>Потокобезопасен без состояния синхронизации: шум берётся из
 * {@code ThreadLocalRandom}, все поля неизменяемы.</p>
 */
public class OrnsteinUhlenbeckGenerator implements ValueGenerator {

    private final double theta;
    private final double mu;
    private final double sigma;

    /**
     * @param theta сила притяжения к среднему (скорость возврата)
     * @param mu базовый уровень — стационарное среднее процесса
     * @param sigma волатильность; масштабируется под ширину нормального
     *        диапазона конкретной метрики
     */
    public OrnsteinUhlenbeckGenerator(double theta, double mu, double sigma) {
        this.theta = theta;
        this.mu = mu;
        this.sigma = sigma;
    }

    /**
     * Один шаг процесса: детерминированный возврат к μ плюс гауссов шум,
     * оба слагаемых масштабированы шагом времени.
     */
    @Override
    public double next(double currentValue, double dtSeconds) {
        double noise = ThreadLocalRandom.current().nextGaussian();
        return currentValue + theta * (mu - currentValue) * dtSeconds
                + sigma * Math.sqrt(dtSeconds) * noise;
    }
}
