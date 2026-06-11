# Patient Monitoring Dashboard

Веб-система симуляции палаты наблюдения: генерация, стриминг и визуализация синтетической физиологической телеметрии для виртуальных пациентов.

## Стек

| Слой | Технология |
|---|---|
| Язык | Java 21 (Virtual Threads / Project Loom) |
| Фреймворк | Spring Boot 3 + WebFlux (реактивный стек) |
| БД | PostgreSQL 16 (R2DBC) |
| Аутентификация | JWT (RFC 7519 / RFC 6750) |
| Стриминг | Server-Sent Events поверх PostgreSQL LISTEN/NOTIFY |
| Формат телеметрии | SenML (RFC 8428) |
| Первичные ключи | UUIDv7 (RFC 9562) |
| Ошибки API | Problem Details (RFC 7807) |
| Временны́е метки | RFC 3339 UTC |

## Быстрый старт

```bash
docker compose up --build
```

| Сервис | URL |
|---|---|
| **Веб-интерфейс (SPA)** | `http://localhost` |
| API | `http://localhost:8080` (или `http://localhost/api/...` через nginx) |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |

Учётные данные по умолчанию: **admin / admin123**

> Для локальной разработки без Docker запустите только базу данных, затем приложение отдельно:
> ```bash
> docker compose up postgres
> ./mvnw spring-boot:run
> ```

## Архитектура

```
┌─────────────────────────────────────────────────────┐
│                   Client SPA                        │
│         (Observer: SSE stream subscription)         │
└───────────────┬──────────────────┬──────────────────┘
                │ REST (JWT Bearer) │ SSE (text/event-stream)
┌───────────────▼──────────────────▼──────────────────┐
│              HTTP API Server (WebFlux)               │
│   AuthController  │  PatientController               │
│   AuthService     │  PatientService / StreamingService│
└───────────────────┬──────────────────────────────────┘
                    │ R2DBC
┌───────────────────▼──────────────────────────────────┐
│                  PostgreSQL                          │
│  users │ patients │ measurements │ critical_incidents│
│                                                      │
│  LISTEN/NOTIFY → StreamingService → Sinks → SSE     │
└──────────────────────┬───────────────────────────────┘
                       │ (INSERT trigger)
┌──────────────────────▼───────────────────────────────┐
│         Metric Generation Engine (Data Plane)        │
│   Executors.newVirtualThreadPerTaskExecutor()        │
│   3 Virtual Threads per patient (one per metric)     │
│   Ornstein–Uhlenbeck stochastic process              │
└──────────────────────────────────────────────────────┘
```

### Ключевые паттерны

- **Virtual Threads** — каждая метрика каждого пациента — независимый поток (`Thread.sleep` без блокировки платформенных потоков)
- **PostgreSQL LISTEN/NOTIFY** — декаплинг генератора и стримингового сервиса без брокера сообщений
- **Фасад приёма (`MeasurementIngestService`)** — единственная точка входа измерений в систему: шов, в котором эмулятор датчиков заменяется на адаптер реальной шины данных больницы без изменения остальной логики
- **Детектор инцидентов (`CriticalIncidentDetector`)** — правило «значение вне нормы — критический эпизод» живёт на пути приёма данных, а не в генераторе, и переживает замену источника
- **Strategy Pattern** — модель генерации значения (`ValueGenerator` / процесс Орнштейна–Уленбека) подменяется, в т.ч. детерминированной в тестах
- **Observer** — фронтенд подписывается на SSE-потоки измерений и инцидентов

### Фронтенд (`frontend/`)

SPA на чистом JavaScript без сборки, отдаётся nginx, который также проксирует `/api` на бэкенд (same-origin, без CORS):

- **Observer** — подписка на SSE через `fetch` + `ReadableStream` (стандартный `EventSource` не умеет передавать заголовок `Authorization: Bearer`, RFC 6750)
- **State management** — кольцевой буфер до 2000 точек на каждую метрику
- **Downsampling** — min/max-бакетирование до ~240 точек при отрисовке (сохраняет пики, которые потерялись бы при прореживании)
- Canvas-графики с зоной нормы; значение вне диапазона подсвечивается красным
- Авто-переподключение SSE при обрыве, сохранение JWT в localStorage

## API

Все защищённые эндпоинты требуют заголовок `Authorization: Bearer <token>`.

### Аутентификация

```
POST /api/v1/auth/token
Content-Type: application/json

{"username": "admin", "password": "admin123"}
```

Ответ:
```json
{"token": "<jwt>"}
```

### Пациенты

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/v1/patients` | Зарегистрировать пациента |
| `GET` | `/api/v1/patients` | Список всех пациентов |
| `GET` | `/api/v1/patients/{id}` | Получить пациента по ID |
| `POST` | `/api/v1/patients/{id}/monitoring/start` | Запустить мониторинг |
| `POST` | `/api/v1/patients/{id}/monitoring/stop` | Остановить мониторинг |
| `GET` | `/api/v1/patients/{id}/stream` | SSE-стрим телеметрии |
| `GET` | `/api/v1/patients/{id}/statistics?from=&to=` | Статистика метрик на интервале |

### Статистика (`GET /statistics`)

Параметры `from`/`to` — RFC 3339 (по умолчанию последний час). Агрегация выполняется в PostgreSQL (`avg`, `var_samp`, `percentile_cont`); диапазонный скан по `measured_at` ускоряется BRIN-индексом.

```json
[{
  "metric": "heart_rate", "unit": "bpm", "count": 485,
  "mean": 75.09, "variance": 14.24,
  "q1": 72.74, "median": 75.26, "q3": 76.95,
  "min": 65.08, "max": 89.11
}]
```

### SSE-поток (SenML, RFC 8428)

```
GET /api/v1/patients/{id}/stream
Accept: text/event-stream

event: metric
data: {"n":"heart_rate","u":"bpm","v":75.2,"t":"2026-06-07T15:21:00.000Z"}

event: metric
data: {"n":"temperature","u":"cel","v":36.7,"t":"2026-06-07T15:21:10.000Z"}
```

### Ошибки (RFC 7807 Problem Details)

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Patient not found",
  "instance": "/api/v1/patients/00000000-0000-0000-0000-000000000000"
}
```

## Математическая модель — Процесс Орнштейна–Уленбека

Физиологическая телеметрия генерируется по формуле:

```
x(t+1) = x(t) + Θ·(μ - x(t))·dt + σ·√dt·N(0,1)
```

| Метрика | Ключ | Единица | μ (базовое) | Норма | Частота |
|---|---|---|---|---|---|
| Частота пульса | `heart_rate` | `bpm` | 75.0 | 60–90 | 1 с |
| ЦВД | `cvp` | `mmHg` | 5.0 | 2.0–8.0 | 3 с |
| Температура | `temperature` | `cel` | 36.6 | 36.5–37.2 | 10 с |

При выходе значения за границы нормы автоматически создаётся запись в таблице `critical_incidents`.

## Схема БД

```sql
users             -- id, username, password_hash (BCrypt)
patients          -- id (UUIDv7), first_name, last_name, is_monitoring_active
measurements      -- id (UUIDv7), patient_id, metric, value, measured_at
                  -- BRIN index on measured_at
critical_incidents -- id (UUIDv7), patient_id, metric, started_at, resolved_at, max_deviation_value
```

## Тесты

```bash
./mvnw test
```

```
AuthServiceTest                  — 3 теста (аутентификация: успех, неверный пароль, пользователь не найден)
AuthControllerIntegrationTest    — 2 теста (HTTP-уровень)
JwtServiceTest                   — 5 тестов (выпуск/проверка JWT, просроченный, чужой ключ, мусорный токен)
AuthenticationManagerTest        — 4 теста (валидный/просроченный/чужой/мусорный токен → 401, не 500)
PatientControllerIntegrationTest — 8 тестов (регистрация, start/stop, 404, история измерений, инциденты)
MetricGeneratorTaskTest          — 2 теста (генерация ≥3 измерений; стоп закрывает инцидент через цепочку ingest)
OrnsteinUhlenbeckGeneratorTest   — 3 теста (возврат к среднему, стационарное среднее ≈ μ)
CriticalIncidentDetectorTest     — 7 тестов (открытие/пик/закрытие эпизода, streamClosed, независимость метрик, неизвестная метрика)
StreamingServiceTest             — 2 теста (SenML-маппинг, фильтрация по пациенту)
IncidentStreamingServiceTest     — 2 теста (доставка и фильтрация событий инцидентов)
```

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `SPRING_R2DBC_URL` | `r2dbc:postgresql://127.0.0.1:5433/patient_db` | URL подключения к БД |
| `SPRING_R2DBC_USERNAME` | `dbuser` | Пользователь БД |
| `SPRING_R2DBC_PASSWORD` | `password` | Пароль БД |

## Техническая документация (Javadoc)

Java-код задокументирован на уровне пакетов, классов и методов (на русском).
Каждый пакет содержит `package-info.java` с назначением, составом и
архитектурными особенностями; у методов описаны контракты, параметры и
поведение при ошибках.

Генерация HTML-сайта документации:

```bash
./mvnw javadoc:javadoc
open target/reports/apidocs/index.html
```

Точка входа для чтения — обзор системы в документации пакета
`maznin.monitoring` (структура пакетов, стек, ключевые решения), оттуда по
ссылкам к остальным пакетам.

## Диаграммы

Архитектурная документация — в [DIAGRAMS.md](DIAGRAMS.md):

- **Диаграммы классов** (Mermaid) — по одной на применённый паттерн: Наблюдатель, Команда + Стратегия, Репозиторий + Persistable, Стратегия в Spring Security
- **Диаграммы последовательности** (Mermaid) — основные сценарии: аутентификация, мониторинг и SSE-поток, жизненный цикл критического инцидента, открытие карточки пациента, восстановление после перезапуска
- **C4-диаграммы** (PlantUML + C4-PlantUML) — контекст, контейнеры, компоненты: `c4-context.puml`, `c4-container.puml`, `c4-component.puml` (готовые SVG лежат рядом)

## Wireframe-макеты интерфейса

Низкодетальные макеты всех экранов с аннотациями поведения — в
[wireframes/WIREFRAMES.md](wireframes/WIREFRAMES.md): вход, панель
наблюдения, карточка пациента (графики, инциденты, статистика), раскрытый
список измерений, плюс карта переходов между экранами. SVG, рендерятся на
GitHub без инструментов.
