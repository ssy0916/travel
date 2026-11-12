CREATE TABLE trips (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    trip_days INT NULL,
    budget INT NULL,
    summary VARCHAR(255) NULL,
    degraded BOOLEAN NULL,
    note VARCHAR(500) NULL,
    payload_hash VARCHAR(64) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_trips_device (device_id),
    INDEX idx_trips_device_city (device_id, city)
);
