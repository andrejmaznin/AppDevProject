package maznin.monitoring.engine;

/**
 * Стратегия генерации следующего значения метрики на каждом тике.
 * Позволяет подменять стохастическую модель, не трогая жизненный цикл задачи
 * (в продакшене — {@link OrnsteinUhlenbeckGenerator}, в тестах —
 * детерминированные последовательности).
 */
public interface ValueGenerator {

    /**
     * Вычисляет значение метрики на следующем тике.
     *
     * @param currentValue текущее значение метрики
     * @param dtSeconds шаг времени в секундах (период тика метрики)
     * @return следующее значение
     */
    double next(double currentValue, double dtSeconds);
}
