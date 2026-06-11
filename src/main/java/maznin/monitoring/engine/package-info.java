/**
 * Эмулятор датчиков витальных показателей.
 *
 * <h2>Назначение</h2>
 * Чистая заглушка источника данных: для каждой пары «пациент × метрика»
 * работает отдельная задача на виртуальном потоке Java 21, которая на каждом
 * тике генерирует следующее значение и отдаёт его в точку приёма
 * ({@link maznin.monitoring.ingest.MeasurementIngestService}). Никакой
 * бизнес-логики мониторинга здесь нет — при интеграции с реальной
 * инфраструктурой больницы пакет целиком заменяется адаптером шины данных,
 * вызывающим тот же {@code ingest()}.
 *
 * <h2>Состав (паттерны: Команда + Стратегия)</h2>
 * <ul>
 *   <li>{@link maznin.monitoring.engine.MetricGenerationEngine} — инициатор:
 *       реестр активных задач ({@code ConcurrentHashMap}) и их запуск/останов
 *       через {@code Executors.newVirtualThreadPerTaskExecutor()};</li>
 *   <li>{@link maznin.monitoring.engine.MetricGeneratorTask} — Команда:
 *       цикл «сгенерировать → отдать в ingest → уснуть на tickRate»;
 *       при завершении сигнализирует {@code streamClosed};</li>
 *   <li>{@link maznin.monitoring.engine.ValueGenerator} — Стратегия генерации
 *       следующего значения;</li>
 *   <li>{@link maznin.monitoring.engine.OrnsteinUhlenbeckGenerator} —
 *       реализация стратегии: стохастический процесс Орнштейна–Уленбека
 *       <i>x(t+1) = x(t) + Θ·(μ − x(t))·dt + σ·√dt·N(0,1)</i>;</li>
 *   <li>{@link maznin.monitoring.engine.MonitoringRestoreRunner} —
 *       восстановление мониторинга после перезапуска приложения по флагу
 *       {@code monitoringActive} в БД.</li>
 * </ul>
 *
 * <h2>Особенности</h2>
 * <ul>
 *   <li>детекция критических инцидентов сознательно вынесена из генератора
 *       в {@link maznin.monitoring.ingest} — она относится к системе
 *       мониторинга и переживает замену источника данных;</li>
 *   <li>σ каждой метрики масштабирована под ширину её нормального диапазона,
 *       чтобы инциденты были редкими событиями, а не шумом;</li>
 *   <li>блокирующие вызовы ({@code sleep}) допустимы — виртуальные потоки
 *       дёшевы и не занимают потоки ОС при ожидании.</li>
 * </ul>
 */
package maznin.monitoring.engine;
