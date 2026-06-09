CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS patients (
    id UUID PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    is_monitoring_active BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS measurements (
    id UUID PRIMARY KEY,
    patient_id UUID REFERENCES patients(id),
    metric VARCHAR(50) CHECK (metric IN ('heart_rate', 'cvp', 'temperature')),
    value NUMERIC,
    measured_at TIMESTAMPTZ
);

-- BRIN index for rapid time-range aggregation (RFC 9112 / time-series optimization)
CREATE INDEX IF NOT EXISTS measurements_measured_at_brin ON measurements USING BRIN (measured_at);

CREATE TABLE IF NOT EXISTS critical_incidents (
    id UUID PRIMARY KEY,
    patient_id UUID REFERENCES patients(id),
    metric VARCHAR(50) CHECK (metric IN ('heart_rate', 'cvp', 'temperature')),
    started_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    max_deviation_value NUMERIC
);

-- Seed an admin user for testing (password: admin123)
-- Hash generated via BCrypt
INSERT INTO users (username, password_hash)
VALUES ('admin', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.TVuHOnu')
ON CONFLICT (username) DO NOTHING;
