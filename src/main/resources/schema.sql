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
    metric VARCHAR(50),
    value NUMERIC,
    measured_at TIMESTAMPTZ
);

-- Seed an admin user for testing (password: admin123)
-- Hash generated via BCrypt
INSERT INTO users (username, password_hash)
VALUES ('admin', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.TVuHOnu')
ON CONFLICT (username) DO NOTHING;
