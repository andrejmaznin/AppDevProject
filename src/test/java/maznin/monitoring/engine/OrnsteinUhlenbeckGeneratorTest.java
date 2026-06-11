package maznin.monitoring.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrnsteinUhlenbeckGeneratorTest {

    @Test
    void withoutNoiseConvergesTowardsMu() {
        // sigma = 0 убирает стохастику: остаётся чистое притяжение к mu
        OrnsteinUhlenbeckGenerator generator = new OrnsteinUhlenbeckGenerator(0.1, 75.0, 0.0);

        double value = 120.0;
        double previousDistance = Math.abs(value - 75.0);
        for (int i = 0; i < 100; i++) {
            value = generator.next(value, 1.0);
            double distance = Math.abs(value - 75.0);
            assertTrue(distance < previousDistance,
                    "Each step must move the value closer to mu, step " + i + ": " + value);
            previousDistance = distance;
        }
        assertEquals(75.0, value, 1.0, "After 100 steps the value should be near mu");
    }

    @Test
    void withoutNoiseStaysAtMu() {
        OrnsteinUhlenbeckGenerator generator = new OrnsteinUhlenbeckGenerator(0.1, 36.6, 0.0);
        assertEquals(36.6, generator.next(36.6, 10.0), 1e-9);
    }

    @Test
    void longRunAverageIsCloseToMu() {
        OrnsteinUhlenbeckGenerator generator = new OrnsteinUhlenbeckGenerator(0.1, 75.0, 2.0);

        double value = 75.0;
        double sum = 0;
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            value = generator.next(value, 1.0);
            sum += value;
        }
        // Стационарное среднее процесса равно mu; допуск с запасом по дисперсии
        assertEquals(75.0, sum / n, 2.0);
    }
}
