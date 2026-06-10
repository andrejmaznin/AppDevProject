package maznin.monitoring.engine;

/**
 * Стратегия генерации следующего значения метрики на каждом тике.
 * Позволяет подменять стохастическую модель, не трогая жизненный цикл задачи.
 */
public interface ValueGenerator {
    double next(double currentValue, double dtSeconds);
}
