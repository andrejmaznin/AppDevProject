# Диаграммы проекта

Mermaid-диаграммы (классы и последовательности) находятся в этом файле и рендерятся
GitHub автоматически. C4-диаграммы — в файлах `c4-context.puml`, `c4-container.puml`,
`c4-component.puml` (PlantUML + C4-PlantUML); инструкция по рендерингу — [в конце файла](#рендеринг-c4-диаграмм).

---

## 1. Диаграммы классов (один паттерн — одна диаграмма)

### 1.1. Наблюдатель / Издатель–Подписчик — потоковая доставка данных

Два конвейера событий построены на `Sinks.Many` (multicast, direct best-effort):
измерения идут через PostgreSQL LISTEN/NOTIFY, инциденты публикуются напрямую
генератором. Браузер подписывается на оба через SSE.

```mermaid
classDiagram
    direction TB

    class StreamingService {
        -sink : Sinks.Many~Measurement~
        +init() void
        +getStream(patientId UUID) Flux~SenMLMeasurement~
    }

    class IncidentStreamingService {
        -sink : Sinks.Many~CriticalIncident~
        +publish(incident CriticalIncident) void
        +getStream(patientId UUID) Flux~CriticalIncident~
    }

    class CriticalIncidentDetector {
        -activeIncidents : Map~String, CriticalIncident~
        +onMeasurement(measurement) void
        +onStreamClosed(patientId, metricKey) void
    }

    class PatientController {
        +getStream(id) Flux~ServerSentEvent~
        +getIncidentStream(id) Flux~ServerSentEvent~
    }

    class PostgreSQL {
        <<внешний издатель>>
        триггер measurement_notify_trigger
        pg_notify на канал measurements_channel
    }

    class Browser {
        <<подписчик>>
        fetch + ReadableStream
        subscribeStream и subscribeIncidentStream
    }

    PostgreSQL ..> StreamingService : NOTIFY после INSERT
    CriticalIncidentDetector ..> IncidentStreamingService : publish() при открытии и резолве
    PatientController --> StreamingService : getStream()
    PatientController --> IncidentStreamingService : getStream()
    Browser ..> PatientController : GET /stream и /incidents/stream (SSE)

    note for StreamingService "Издатель измерений:<br/>слушает канал PostgreSQL,<br/>эмитит во внутренний sink"
    note for IncidentStreamingService "Издатель инцидентов:<br/>уведомление сразу после<br/>фиксации в БД"
```

### 1.2. Команда + Стратегия — эмулятор датчиков

`MetricGeneratorTask` — Команда (инкапсулированная работа, выполняемая на
виртуальном потоке), `MetricGenerationEngine` — инициатор с реестром активных
задач. Формула генерации значения выделена в Стратегию `ValueGenerator`.
Единственная связь эмулятора с системой мониторинга — вызов
`MeasurementIngestService` (см. 1.5).

```mermaid
classDiagram
    direction TB

    class MetricGenerationEngine {
        <<Service>>
        -tasks : Map~String, MetricGeneratorTask~
        -executorService : ExecutorService
        +startMonitoring(patientId UUID) void
        +stopMonitoring(patientId UUID) void
    }

    class MetricGeneratorTask {
        -running : AtomicBoolean
        -currentValue : double
        +run() void
        +stop() void
    }

    class Runnable {
        <<interface>>
        +run() void
    }

    class ValueGenerator {
        <<interface>>
        +next(currentValue double, dtSeconds double) double
    }

    class OrnsteinUhlenbeckGenerator {
        -theta : double
        -mu : double
        -sigma : double
        +next(currentValue, dtSeconds) double
    }

    class Metric {
        <<enumeration>>
        HEART_RATE
        CVP
        TEMPERATURE
        -key : String
        -unit : String
        -mu : double
        -rangeMin : double
        -rangeMax : double
        -tickRateMs : long
        -sigma : double
    }

    class MeasurementIngestService {
        <<Service>>
        +ingest(measurement) void
        +streamClosed(patientId, metricKey) void
    }

    class MonitoringRestoreRunner {
        <<ApplicationRunner>>
        +run(args) void
    }

    Runnable <|.. MetricGeneratorTask
    ValueGenerator <|.. OrnsteinUhlenbeckGenerator
    MetricGenerationEngine "1" o-- "0..*" MetricGeneratorTask : реестр задач
    MetricGeneratorTask --> ValueGenerator : использует стратегию
    MetricGeneratorTask --> Metric : параметры метрики
    MetricGeneratorTask ..> MeasurementIngestService : ingest() / streamClosed()
    MonitoringRestoreRunner ..> MetricGenerationEngine : восстановление при старте

    note for MetricGenerationEngine "Инициатор: одна задача<br/>на пару пациент+метрика,<br/>виртуальные потоки Java 21"
    note for OrnsteinUhlenbeckGenerator "x(t+1) = x(t) + Θ·(μ−x)·dt + σ·√dt·N(0,1)"
    note for MeasurementIngestService "Шов: при замене эмулятора<br/>на шину данных больницы<br/>адаптер вызывает те же методы"
```

### 1.5. Фасад приёма измерений — шов источника данных

`MeasurementIngestService` — Фасад: «измерение вошло в систему» = сохранение
в БД + детекция инцидентов. `CriticalIncidentDetector` — правило предметной
области, отвязанное от происхождения данных: при замене эмулятора на
реальную шину больницы детекция продолжает работать без изменений.

```mermaid
classDiagram
    direction TB

    class MeasurementIngestService {
        <<Service, Фасад>>
        +ingest(measurement) void
        +streamClosed(patientId, metricKey) void
    }

    class CriticalIncidentDetector {
        <<Service>>
        -activeIncidents : Map~String, CriticalIncident~
        +onMeasurement(measurement) void
        +onStreamClosed(patientId, metricKey) void
        -openIncident(key, measurement) void
        -resolveIncident(key, incident, resolvedAt) void
    }

    class MetricGeneratorTask {
        <<продюсер сегодня>>
    }

    class HospitalBusAdapter {
        <<продюсер в перспективе>>
    }

    class MeasurementRepository {
        <<interface>>
    }
    class CriticalIncidentRepository {
        <<interface>>
    }
    class IncidentStreamingService {
        +publish(incident) void
    }

    MetricGeneratorTask ..> MeasurementIngestService : ingest()
    HospitalBusAdapter ..> MeasurementIngestService : ingest()
    MeasurementIngestService --> MeasurementRepository : save() асинхронно
    MeasurementIngestService --> CriticalIncidentDetector : onMeasurement()
    CriticalIncidentDetector --> CriticalIncidentRepository : INSERT (block) / UPDATE (async)
    CriticalIncidentDetector --> IncidentStreamingService : publish() при открытии и резолве

    note for CriticalIncidentDetector "Конечный автомат эпизода<br/>по каждой паре пациент×метрика;<br/>контракт: события одной пары<br/>поступают последовательно"
```

### 1.3. Репозиторий + Persistable — слой доступа к данным

Все сущности с UUID-идентификаторами реализуют `Persistable`, поскольку UUIDv7
присваивается в коде приложения: без этого Spring Data R2DBC принимал бы
непустой `@Id` за признак существующей записи и выполнял UPDATE вместо INSERT.

```mermaid
classDiagram
    direction TB

    class ReactiveCrudRepository~T, ID~ {
        <<interface>>
        +save(entity) Mono~T~
        +findById(id) Mono~T~
        +findAll() Flux~T~
    }

    class Persistable~UUID~ {
        <<interface>>
        +getId() UUID
        +isNew() boolean
    }

    class PatientRepository {
        <<interface>>
    }
    class MeasurementRepository {
        <<interface>>
        +findRecentByPatientId(patientId, limit) Flux~Measurement~
    }
    class CriticalIncidentRepository {
        <<interface>>
        +findTop20ByPatientIdOrderByStartedAtDesc(patientId) Flux~CriticalIncident~
    }
    class UserRepository {
        <<interface>>
        +findByUsername(username) Mono~User~
    }

    class Patient {
        -id : UUID
        -firstName : String
        -lastName : String
        -monitoringActive : boolean
        -newEntity : boolean @Transient
        +isNew() boolean
        +markNotNew() void
    }
    class Measurement {
        -id : UUID
        -patientId : UUID
        -metric : String
        -value : Double
        -measuredAt : OffsetDateTime
    }
    class CriticalIncident {
        -id : UUID
        -patientId : UUID
        -metric : String
        -startedAt : OffsetDateTime
        -resolvedAt : OffsetDateTime
        -maxDeviationValue : Double
    }

    ReactiveCrudRepository <|-- PatientRepository
    ReactiveCrudRepository <|-- MeasurementRepository
    ReactiveCrudRepository <|-- CriticalIncidentRepository
    ReactiveCrudRepository <|-- UserRepository

    Persistable <|.. Patient
    Persistable <|.. Measurement
    Persistable <|.. CriticalIncident

    PatientRepository ..> Patient
    MeasurementRepository ..> Measurement
    CriticalIncidentRepository ..> CriticalIncident

    note for Persistable "ID = UUIDv7 присваивается в коде,<br/>поэтому isNew() управляется флагом<br/>@Transient newEntity"
    note for MeasurementRepository "row_number() OVER (PARTITION BY metric)<br/>— последние N точек по каждой метрике"
```

### 1.4. Стратегия (точки расширения Spring Security) — JWT-аутентификация

Собственные реализации подменяют стандартное поведение цепочки фильтров
WebFlux Security: извлечение контекста из заголовка Bearer и проверка JWT.

```mermaid
classDiagram
    direction TB

    class ReactiveAuthenticationManager {
        <<interface>>
        +authenticate(authentication) Mono~Authentication~
    }

    class ServerSecurityContextRepository {
        <<interface>>
        +load(exchange) Mono~SecurityContext~
        +save(exchange, context) Mono~Void~
    }

    class AuthenticationManager {
        +authenticate(authentication) Mono~Authentication~
    }

    class SecurityContextRepository {
        +load(exchange) Mono~SecurityContext~
    }

    class JwtService {
        -secretKey : String
        -jwtExpiration : long
        +generateToken(userDetails) String
        +extractUsername(token) String
        +isTokenValid(token, userDetails) boolean
    }

    class SecurityConfig {
        <<Configuration>>
        +securityWebFilterChain(http) SecurityWebFilterChain
        +passwordEncoder() PasswordEncoder
        +corsWebFilter() CorsWebFilter
    }

    class AuthService {
        +authenticate(request) Mono~AuthResponse~
    }

    ReactiveAuthenticationManager <|.. AuthenticationManager
    ServerSecurityContextRepository <|.. SecurityContextRepository
    SecurityContextRepository --> AuthenticationManager : делегирует
    AuthenticationManager --> JwtService : проверка токена
    AuthService --> JwtService : выпуск токена
    SecurityConfig --> AuthenticationManager : встраивает в цепочку
    SecurityConfig --> SecurityContextRepository : встраивает в цепочку

    note for SecurityConfig "401 и 403 отдаются в формате<br/>RFC 9457 Problem Details<br/>с типами из каталога ProblemType"
    note for JwtService "JWT HS256, срок 24 ч (RFC 7519),<br/>передача Bearer (RFC 6750)"
```

---

## 2. Диаграммы последовательности

### 2.1. Получение JWT-токена

```mermaid
sequenceDiagram
    autonumber
    actor B as Браузер (SPA)
    participant AC as AuthController
    participant AS as AuthService
    participant UR as UserRepository
    participant PE as PasswordEncoder (BCrypt)
    participant JS as JwtService

    B->>AC: POST /api/v1/auth/token {username, password}
    AC->>AS: authenticate(request)
    AS->>UR: findByUsername(username)
    UR-->>AS: Mono~User~
    AS->>PE: matches(password, passwordHash)
    alt пароль верен
        AS->>JS: generateToken(user)
        JS-->>AS: JWT (HS256, 24 ч)
        AS-->>B: 200 {token}
    else пользователь не найден или пароль неверен
        AS-->>B: 401 Problem Details (RFC 7807)
    end
```

### 2.2. Доступ к защищённому ресурсу

```mermaid
sequenceDiagram
    autonumber
    actor B as Браузер (SPA)
    participant FC as SecurityWebFilterChain
    participant SCR as SecurityContextRepository
    participant AM as AuthenticationManager
    participant JS as JwtService
    participant PC as PatientController

    B->>FC: GET /api/v1/patients (Authorization: Bearer jwt)
    FC->>SCR: load(exchange)
    SCR->>AM: authenticate(token)
    AM->>JS: extractUsername(token), isTokenValid(...)
    alt токен валиден
        JS-->>AM: username
        AM-->>FC: Authentication (ROLE_USER)
        FC->>PC: запрос пропущен
        PC-->>B: 200 данные
    else токен повреждён или просрочен
        AM-->>FC: Mono.empty()
        FC-->>B: 401 Problem Details (RFC 7807)
    end
```

### 2.3. Старт мониторинга и поток измерений в реальном времени

```mermaid
sequenceDiagram
    autonumber
    actor B as Браузер (SPA)
    participant PC as PatientController
    participant PS as PatientService
    participant PR as PatientRepository
    participant EN as MetricGenerationEngine
    participant T as MetricGeneratorTask (виртуальный поток, x3)
    participant ING as MeasurementIngestService
    participant PG as PostgreSQL
    participant SS as StreamingService

    B->>PC: POST /patients/{id}/monitoring/start
    PC->>PS: startMonitoring(id)
    PS->>PR: findById(id)
    alt пациент не найден
        PS-->>B: 404 Problem Details
    else найден
        PS->>PR: save(monitoringActive = true)
        PS->>EN: startMonitoring(id)
        EN->>T: submit() — по задаче на каждую метрику
        PC-->>B: 200 OK
    end

    B->>PC: GET /patients/{id}/stream (SSE)
    PC->>SS: getStream(id)
    SS-->>B: открытое SSE-соединение

    loop каждый тик (пульс 1 с, ЦВД 3 с, температура 10 с)
        T->>T: valueGenerator.next(x, dt) — процесс Орнштейна–Уленбека
        T->>ING: ingest(Measurement)
        ING->>PG: INSERT INTO measurements (+ детекция инцидентов, см. 2.4)
        PG--)SS: NOTIFY measurements_channel (триггер)
        SS->>SS: sink.tryEmitNext(measurement)
        SS--)B: event: metric, data: {n,u,v,t} (SenML, RFC 8428)
        B->>B: буфер + перерисовка графика Canvas
    end
```

### 2.4. Жизненный цикл критического инцидента

```mermaid
sequenceDiagram
    autonumber
    participant T as MetricGeneratorTask (продюсер)
    participant ING as MeasurementIngestService
    participant DET as CriticalIncidentDetector
    participant PG as PostgreSQL
    participant IS as IncidentStreamingService
    actor B as Браузер (SPA)

    T->>ING: ingest(measurement)
    ING->>DET: onMeasurement(measurement)
    Note over DET: значение вышло за границы нормы,<br/>эпизода по паре пациент×метрика нет
    DET->>PG: INSERT INTO critical_incidents — синхронно, block()
    DET->>DET: markNotNew(), запомнить в activeIncidents
    DET->>IS: publish(incident)
    IS--)B: event: incident (resolvedAt = null)
    B->>B: строка «активен», красный бейдж-счётчик

    loop пока значение вне нормы
        T->>ING: ingest(measurement)
        ING->>DET: onMeasurement(measurement)
        DET->>DET: обновление maxDeviationValue (пик отклонения от μ)
    end

    alt значение вернулось в норму
        T->>ING: ingest(measurement в норме)
        ING->>DET: onMeasurement(measurement)
    else мониторинг остановлен
        T->>ING: streamClosed(patientId, metric)
        ING->>DET: onStreamClosed(patientId, metric)
    end
    DET->>DET: setResolvedAt(now), убрать из activeIncidents
    DET->>IS: publish(incident)
    IS--)B: event: incident (resolvedAt задан)
    B->>B: строка завершена: время конца, длительность
    DET->>PG: UPDATE critical_incidents — асинхронно
```

### 2.5. Открытие карточки пациента

```mermaid
sequenceDiagram
    autonumber
    actor B as Браузер (SPA)
    participant PC as PatientController
    participant MR as MeasurementRepository
    participant CR as CriticalIncidentRepository
    participant ST as StatisticsService
    participant PG as PostgreSQL

    B->>B: selectPatient(id) — сброс буферов, закрытие старых SSE

    par история измерений
        B->>PC: GET /patients/{id}/measurements?limit=100
        PC->>MR: findRecentByPatientId(id, 100)
        MR->>PG: row_number() OVER (PARTITION BY metric ...)
        PG-->>B: последние 100 точек на метрику (SenML)
        B->>B: предзаполнение буферов, отрисовка графиков
    and история инцидентов
        B->>PC: GET /patients/{id}/incidents
        PC->>CR: findTop20ByPatientIdOrderByStartedAtDesc(id)
        PG-->>B: последние 20 инцидентов
    and статистика
        B->>PC: GET /patients/{id}/statistics?from=&to=
        PC->>ST: getStatistics(id, from, to)
        ST->>PG: avg, var_samp, percentile_cont(0.25/0.5/0.75) GROUP BY metric
        PG-->>B: среднее, дисперсия, квартили, мин, макс, N
    end

    B->>PC: GET /patients/{id}/stream (SSE-подписка на измерения)
    B->>PC: GET /patients/{id}/incidents/stream (SSE-подписка на инциденты)
```

### 2.6. Восстановление мониторинга после перезапуска

```mermaid
sequenceDiagram
    autonumber
    participant SB as Spring Boot (ApplicationRunner)
    participant RR as MonitoringRestoreRunner
    participant PR as PatientRepository
    participant EN as MetricGenerationEngine

    SB->>RR: run(args) — после старта контекста
    RR->>PR: findAll()
    PR-->>RR: Flux~Patient~
    loop для каждого пациента с monitoringActive = true
        RR->>EN: startMonitoring(patientId)
        EN->>EN: submit задач генерации на виртуальные потоки
    end
    Note over RR,EN: состояние мониторинга переживает перезапуск контейнера
```

---

## 3. C4-диаграммы

Mermaid C4 ограничен, поэтому C4-диаграммы выполнены на **PlantUML + C4-PlantUML**
(стандарт de facto для C4): файлы `c4-context.puml`, `c4-container.puml`,
`c4-component.puml` в корне проекта. Рядом лежат уже отрендеренные
`c4-context.svg`, `c4-container.svg`, `c4-component.svg` — их можно открыть
без каких-либо инструментов.

### Рендеринг C4-диаграмм

Любой из способов:

**Docker (ничего не устанавливая):**
```bash
docker run --rm -v "$PWD":/data plantuml/plantuml -tsvg /data/c4-context.puml /data/c4-container.puml /data/c4-component.puml
```
SVG-файлы появятся рядом с исходниками.

**IntelliJ IDEA / VS Code:** плагин «PlantUML Integration» (IDEA) или «PlantUML»
(VS Code, jebbs.plantuml) — предпросмотр по открытию `.puml` файла.

**Онлайн:** содержимое файла → <https://www.plantuml.com/plantuml> или любой
сервер Kroki.

Диаграммы используют `!include` C4-PlantUML по URL, поэтому при первом
рендеринге нужен доступ в интернет (или скачайте `C4*.puml` из
<https://github.com/plantuml-stdlib/C4-PlantUML> рядом и замените включения на
локальные).

---

## 4. ERD сущностей базы данных

Четыре таблицы; `measurements` и `critical_incidents` ссылаются на
`patients` (FK). `users` — изолированная таблица аутентификации.
Первичные ключи доменных сущностей — UUIDv7, присваиваются в приложении.

```mermaid
erDiagram
    PATIENTS {
        UUID id PK "UUIDv7, присваивается в приложении"
        VARCHAR first_name
        VARCHAR last_name
        BOOLEAN is_monitoring_active "источник истины для восстановления"
    }
    MEASUREMENTS {
        UUID id PK "UUIDv7"
        UUID patient_id FK
        VARCHAR metric "CHECK: heart_rate | cvp | temperature"
        NUMERIC value
        TIMESTAMPTZ measured_at "BRIN-индекс"
    }
    CRITICAL_INCIDENTS {
        UUID id PK "UUIDv7"
        UUID patient_id FK
        VARCHAR metric "CHECK: heart_rate | cvp | temperature"
        TIMESTAMPTZ started_at "не NULL"
        TIMESTAMPTZ resolved_at "NULL у активного эпизода"
        NUMERIC max_deviation_value "пик отклонения от мю"
    }
    USERS {
        SERIAL id PK
        VARCHAR username UK
        VARCHAR password_hash "BCrypt"
    }

    PATIENTS ||--o{ MEASUREMENTS : "измерения"
    PATIENTS ||--o{ CRITICAL_INCIDENTS : "эпизоды"
```
