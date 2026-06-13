-- =====================================================================
-- Patient Monitoring Dashboard — начальная миграция схемы БД (PostgreSQL)
-- =====================================================================
-- Версия: V1 (именование в стиле Flyway: V<версия>__<описание>.sql).
--
-- Назначение: полное и идемпотентное провижининг схемы для развёртывания
-- против существующего экземпляра PostgreSQL — для ручного применения
-- (`psql -f`), ревью DBA и как ассет релиза на GitHub.
--
-- Отличие от src/main/resources/schema.sql:
--   * schema.sql — портируемый DDL, выполняется приложением при старте
--     (spring.sql.init) и должен оставаться совместимым с H2 (тесты),
--     поэтому НЕ содержит plpgsql-триггер;
--   * этот файл — полная Postgres-версия, включает функцию и триггер
--     pg_notify, поэтому БД оказывается полностью готовой ещё до первого
--     запуска приложения. Приложение в любом случае пересоздаёт триггер
--     идемпотентно (CREATE OR REPLACE / DROP IF EXISTS), конфликта нет.
--
-- Применение:
--   psql "postgresql://<user>:<password>@<host>:<port>/<database>" \
--        -f db/migration/V1__init.sql
-- =====================================================================

-- --- Учётные записи операторов -----------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255)        NOT NULL          -- BCrypt
);

-- --- Пациенты ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS patients (
    id                   UUID PRIMARY KEY,              -- UUIDv7, присваивается приложением
    first_name           VARCHAR(255),
    last_name            VARCHAR(255),
    is_monitoring_active BOOLEAN DEFAULT FALSE          -- источник истины для восстановления при старте
);

-- --- Измерения (append-only) -------------------------------------------
CREATE TABLE IF NOT EXISTS measurements (
    id          UUID PRIMARY KEY,                       -- UUIDv7
    patient_id  UUID REFERENCES patients(id),
    metric      VARCHAR(50) CHECK (metric IN ('heart_rate', 'cvp', 'temperature')),
    value       NUMERIC,
    measured_at TIMESTAMPTZ
);

-- BRIN-индекс для быстрых диапазонных выборок по времени:
-- хранит min/max на диапазон страниц, эффективен при монотонном measured_at.
CREATE INDEX IF NOT EXISTS measurements_measured_at_brin
    ON measurements USING BRIN (measured_at);

-- --- Критические инциденты ---------------------------------------------
CREATE TABLE IF NOT EXISTS critical_incidents (
    id                  UUID PRIMARY KEY,               -- UUIDv7
    patient_id          UUID REFERENCES patients(id),
    metric              VARCHAR(50) CHECK (metric IN ('heart_rate', 'cvp', 'temperature')),
    started_at          TIMESTAMPTZ NOT NULL,
    resolved_at         TIMESTAMPTZ,                    -- NULL у активного эпизода
    max_deviation_value NUMERIC                         -- пик отклонения от μ за эпизод
);

-- --- Шина событий: pg_notify при вставке измерения ---------------------
-- Триггер транслирует каждую вставку в канал measurements_channel;
-- бэкенд слушает его (LISTEN) и раздаёт подписчикам через SSE.
CREATE OR REPLACE FUNCTION notify_measurement() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('measurements_channel', row_to_json(NEW)::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS measurement_notify_trigger ON measurements;

CREATE TRIGGER measurement_notify_trigger
    AFTER INSERT ON measurements
    FOR EACH ROW EXECUTE PROCEDURE notify_measurement();

-- --- Стартовая учётная запись ------------------------------------------
-- Логин admin, пароль admin123 (BCrypt-хэш). Идемпотентно: при повторном
-- применении хэш обновляется, дубликат не создаётся.
INSERT INTO users (username, password_hash)
VALUES ('admin', '$2a$10$X0oSdF3hK21JokdYMHkaVeC4UgrmXNrLXRiWpiUHLGOmAfD8vvFE2')
ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;
