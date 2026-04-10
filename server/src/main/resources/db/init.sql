SET client_encoding TO 'UTF8';

-- ─── Enums ────────────────────────────────────────────────────────────────────

CREATE TYPE app_role AS ENUM ('admin', 'user');

CREATE TYPE room_status AS ENUM (
    'available', 'occupied', 'cleaning', 'maintenance', 'out_of_service'
);

CREATE TYPE hotel_user_role AS ENUM ('admin', 'manager', 'viewer');

CREATE TYPE reservation_status AS ENUM (
    'pending', 'confirmed', 'checked_in', 'checked_out', 'cancelled', 'no_show'
);

CREATE TYPE reservation_source_type AS ENUM ('private', 'external');

CREATE TYPE deposit_status AS ENUM (
    'not_required', 'pending', 'paid', 'forfeited'
);

CREATE TYPE pricing_adjustment_type AS ENUM ('deduction', 'surcharge');

CREATE TYPE pricing_adjustment_reason AS ENUM (
    'discount', 'coupon', 'manual_discount', 'cleaning_fee',
    'city_tax', 'service_fee', 'late_checkout', 'other'
);

CREATE TYPE invoice_status AS ENUM (
    'draft', 'issued', 'paid', 'overdue', 'cancelled'
);

CREATE TYPE payment_method AS ENUM ('cash', 'card', 'bank_transfer', 'online');

CREATE TYPE payment_status AS ENUM (
    'pending', 'completed', 'failed', 'refunded'
);

-- ─── Hotels ───────────────────────────────────────────────────────────────────

CREATE TABLE hotels (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR   NOT NULL,
    address    VARCHAR,
    phone      VARCHAR,
    email      VARCHAR,
    timezone   VARCHAR,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── Rooms ────────────────────────────────────────────────────────────────────

CREATE TABLE room_types (
    id          SERIAL PRIMARY KEY,
    hotel_id    INTEGER NOT NULL REFERENCES hotels(id),
    name        VARCHAR NOT NULL,
    description TEXT,
    UNIQUE (hotel_id, name)
);

CREATE TABLE rooms (
    id           SERIAL PRIMARY KEY,
    hotel_id     INTEGER     NOT NULL REFERENCES hotels(id),
    room_type_id INTEGER     NOT NULL REFERENCES room_types(id),
    number       VARCHAR     NOT NULL,
    floor        INTEGER,
    max_guests   INTEGER     NOT NULL,
    status       room_status NOT NULL DEFAULT 'available',
    description  TEXT,
    archived_at  TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (hotel_id, number)
);

CREATE TABLE tags (
    id   SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL UNIQUE
);

CREATE TABLE room_tags (
    room_id INTEGER NOT NULL REFERENCES rooms(id),
    tag_id  INTEGER NOT NULL REFERENCES tags(id),
    PRIMARY KEY (room_id, tag_id)
);

-- ─── Pricing ─────────────────────────────────────────────────────────────────

CREATE TABLE room_night_prices (
    id               SERIAL PRIMARY KEY,
    room_id          INTEGER NOT NULL REFERENCES rooms(id),
    date             DATE    NOT NULL,
    price_per_person DECIMAL NOT NULL,
    currency         VARCHAR NOT NULL,
    UNIQUE (room_id, date)
);

-- ─── Guests & Users ───────────────────────────────────────────────────────────

CREATE TABLE guests (
    id          SERIAL PRIMARY KEY,
    first_name  VARCHAR   NOT NULL,
    last_name   VARCHAR   NOT NULL,
    email       VARCHAR,
    phone       VARCHAR,
    id_number   VARCHAR,
    nationality VARCHAR,
    blacklisted BOOLEAN   NOT NULL DEFAULT FALSE,
    notes       TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR   NOT NULL,
    email      VARCHAR   NOT NULL UNIQUE,
    app_role   app_role  NOT NULL DEFAULT 'user',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_hotel_roles (
    user_id  INTEGER         NOT NULL REFERENCES users(id),
    hotel_id INTEGER         NOT NULL REFERENCES hotels(id),
    role     hotel_user_role NOT NULL,
    PRIMARY KEY (user_id, hotel_id)
);

-- ─── Reservations ─────────────────────────────────────────────────────────────

CREATE TABLE reservations (
    id               SERIAL PRIMARY KEY,
    hotel_id         INTEGER                NOT NULL REFERENCES hotels(id),
    room_id          INTEGER                NOT NULL REFERENCES rooms(id),
    guest_id         INTEGER                NOT NULL REFERENCES guests(id),
    created_by       INTEGER                REFERENCES users(id),

    source_type      reservation_source_type,
    source_name      VARCHAR,
    external_ref     VARCHAR,

    check_in_date    DATE               NOT NULL,
    check_out_date   DATE               NOT NULL,
    check_in_time    TIMESTAMP,
    check_out_time   TIMESTAMP,

    status           reservation_status NOT NULL DEFAULT 'pending',
    adults           INTEGER            NOT NULL DEFAULT 1,
    children         INTEGER            NOT NULL DEFAULT 0,

    currency         VARCHAR,
    subtotal_amount  DECIMAL,
    deduction_amount DECIMAL            NOT NULL DEFAULT 0,
    extra_fee_amount DECIMAL            NOT NULL DEFAULT 0,
    total_amount     DECIMAL,

    deposit_amount   DECIMAL,
    deposit_due_date DATE,
    deposit_status   deposit_status     NOT NULL DEFAULT 'not_required',
    deposit_paid_at  TIMESTAMP,

    price_overridden BOOLEAN            NOT NULL DEFAULT FALSE,
    price_snapshot   JSONB,
    price_note       TEXT,

    notes            TEXT,
    created_at       TIMESTAMP          NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP,
    deleted_at       TIMESTAMP
);

CREATE TABLE reservation_price_adjustments (
    id             SERIAL PRIMARY KEY,
    reservation_id INTEGER                   NOT NULL REFERENCES reservations(id),
    type           pricing_adjustment_type   NOT NULL,
    reason         pricing_adjustment_reason NOT NULL DEFAULT 'other',
    label          VARCHAR,
    amount         DECIMAL                   NOT NULL,
    notes          TEXT,
    created_at     TIMESTAMP                 NOT NULL DEFAULT NOW()
);

-- ─── Invoices & Payments ──────────────────────────────────────────────────────

CREATE TABLE invoices (
    id             SERIAL PRIMARY KEY,
    reservation_id INTEGER        NOT NULL REFERENCES reservations(id),
    invoice_number VARCHAR        NOT NULL UNIQUE,
    issued_at      TIMESTAMP,
    due_date       DATE,
    total_amount   DECIMAL        NOT NULL,
    paid_amount    DECIMAL        NOT NULL DEFAULT 0,
    status         invoice_status NOT NULL DEFAULT 'draft'
);

CREATE TABLE payments (
    id             SERIAL PRIMARY KEY,
    reservation_id INTEGER        NOT NULL REFERENCES reservations(id),
    invoice_id     INTEGER        REFERENCES invoices(id),
    amount         DECIMAL        NOT NULL,
    currency       VARCHAR,
    method         payment_method NOT NULL,
    status         payment_status NOT NULL DEFAULT 'pending',
    is_deposit     BOOLEAN        NOT NULL DEFAULT FALSE,
    paid_at        TIMESTAMP,
    notes          TEXT
);

-- ─── Operations ───────────────────────────────────────────────────────────────

CREATE TABLE room_blocks (
    id         SERIAL PRIMARY KEY,
    room_id    INTEGER   NOT NULL REFERENCES rooms(id),
    created_by INTEGER   REFERENCES users(id),
    from_date  DATE      NOT NULL,
    to_date    DATE      NOT NULL,
    reason     VARCHAR,
    notes      TEXT
);

CREATE TABLE audit_log (
    id         SERIAL PRIMARY KEY,
    table_name VARCHAR   NOT NULL,
    record_id  INTEGER   NOT NULL,
    action     VARCHAR   NOT NULL,
    changed_by INTEGER   REFERENCES users(id),
    changed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    diff       JSONB
);

-- ─── Seed data ────────────────────────────────────────────────────────────────

INSERT INTO users (name, email, app_role, created_at) VALUES
    ('Admin Admin', 'admin.admin@reserveo.dev', 'admin', '2025-06-01 10:10:00'),
    ('Anna Kowalska', 'anna.kowalska@reserveo.dev', 'user', '2025-06-01 10:00:00'),
    ('Marek Nowak',   'marek.nowak@reserveo.dev',   'user',  '2025-06-01 10:05:00'),
    ('Tomasz Działowy',   'tomasz.dzialowy@reserveo.dev',   'user',  '2025-06-01 10:05:00');;

INSERT INTO hotels (name, address, phone, email, timezone, created_at) VALUES
    ('The Grand Palace', 'ul. Królewska 1, 00-001 Warszawa', '+48 22 100 2000', 'contact@grandpalace.pl', 'Europe/Warsaw', '2025-06-01 10:10:00'),
    ('Seaside Resort',   'ul. Plażowa 5, 81-001 Gdynia',     '+48 58 300 4000', 'info@seasideresort.pl',  'Europe/Warsaw', '2025-06-01 10:15:00');

INSERT INTO user_hotel_roles (user_id, hotel_id, role) VALUES
    (2, 1, 'admin'),
    (2, 2, 'viewer'),
    (3, 2, 'admin'),
    (3, 1, 'viewer'),
    (4, 1, 'manager'),
    (4, 2, 'manager');

INSERT INTO room_types (hotel_id, name) VALUES
    (1, 'Single'), (1, 'Double'), (1, 'Suite'),
    (2, 'Standard'), (2, 'Deluxe'), (2, 'Penthouse');

INSERT INTO rooms (hotel_id, room_type_id, number, floor, max_guests, status, description) VALUES
    (1, 1, '101', 1, 1, 'available',  'Cosy single room with city view'),
    (1, 1, '102', 1, 1, 'available',  'Cosy single room with courtyard view'),
    (1, 2, '201', 2, 2, 'occupied',   'Spacious double room with king bed'),
    (1, 2, '202', 2, 2, 'cleaning',   'Spacious double room with twin beds'),
    (1, 3, '301', 3, 4, 'available',  'Luxury suite with panoramic view'),
    (1, 3, '302', 3, 4, 'maintenance','Luxury suite with private terrace'),
    (1, 1, '103', 1, 1, 'available',  NULL),
    (1, 2, '203', 2, 2, 'available',  NULL),
    (1, 3, '303', 3, 3, 'available',  'Junior suite'),
    (1, 2, '204', 2, 2, 'available',  NULL),
    (2, 4, '101', 1, 2, 'available',  'Standard room with garden view'),
    (2, 4, '102', 1, 2, 'occupied',   'Standard room with pool view'),
    (2, 5, '201', 2, 2, 'available',  'Deluxe room with sea view'),
    (2, 5, '202', 2, 2, 'available',  'Deluxe room with balcony'),
    (2, 6, '301', 3, 4, 'available',  'Penthouse with private rooftop'),
    (2, 6, '302', 3, 4, 'cleaning',   'Penthouse with sea panorama'),
    (2, 4, '103', 1, 2, 'available',  NULL),
    (2, 5, '203', 2, 2, 'available',  NULL),
    (2, 4, '104', 1, 2, 'available',  NULL),
    (2, 5, '204', 2, 3, 'available',  NULL);

INSERT INTO guests (first_name, last_name, email, phone, nationality, blacklisted, created_at) VALUES
    ('Jan',     'Wiśniewski',  'jan.wisniewski@gmail.com',   '+48 601 111 001', 'PL', FALSE, '2025-07-01 09:00:00'),
    ('Maria',   'Dąbrowska',   'maria.dabrowska@gmail.com',  '+48 601 111 002', 'PL', FALSE, '2025-07-02 09:00:00'),
    ('Thomas',  'Müller',      'thomas.mueller@gmail.com',   '+49 151 111 003', 'DE', FALSE, '2025-08-01 09:00:00'),
    ('Sophie',  'Dupont',      'sophie.dupont@gmail.com',    '+33 6 11 11 004', 'FR', FALSE, '2025-08-05 09:00:00'),
    ('Piotr',   'Zielski',     'piotr.zielski@gmail.com',    '+48 601 111 005', 'PL', FALSE, '2025-09-01 09:00:00'),
    ('Elena',   'Rossi',       'elena.rossi@gmail.com',      '+39 333 111 006', 'IT', FALSE, '2025-09-10 09:00:00'),
    ('Adam',    'Lewandowski', 'adam.lewandowski@gmail.com', '+48 601 111 007', 'PL', FALSE, '2025-10-01 09:00:00'),
    ('Natalia', 'Wójcik',      'natalia.wojcik@gmail.com',   '+48 601 111 008', 'PL', FALSE, '2025-10-15 09:00:00');

INSERT INTO room_night_prices (room_id, date, price_per_person, currency)
SELECT
    r.id,
    d::date,
    CASE
        WHEN r.room_type_id = 1 THEN CASE WHEN d BETWEEN '2025-12-20' AND '2025-12-26' THEN 120 WHEN d BETWEEN '2025-12-27' AND '2026-01-06' THEN 160 WHEN d BETWEEN '2026-07-01' AND '2026-12-19' THEN 104 ELSE 80 END
        WHEN r.room_type_id = 2 THEN CASE WHEN d BETWEEN '2025-12-20' AND '2025-12-26' THEN 180 WHEN d BETWEEN '2025-12-27' AND '2026-01-06' THEN 240 WHEN d BETWEEN '2026-07-01' AND '2026-12-19' THEN 156 ELSE 120 END
        WHEN r.room_type_id = 3 THEN CASE WHEN d BETWEEN '2025-12-20' AND '2025-12-26' THEN 375 WHEN d BETWEEN '2025-12-27' AND '2026-01-06' THEN 500 WHEN d BETWEEN '2026-07-01' AND '2026-12-19' THEN 325 ELSE 250 END
        WHEN r.room_type_id = 4 THEN CASE WHEN d BETWEEN '2025-12-20' AND '2025-12-26' THEN 135 WHEN d BETWEEN '2025-12-27' AND '2026-01-06' THEN 180 WHEN d BETWEEN '2026-07-01' AND '2026-12-19' THEN 117 ELSE 90 END
        WHEN r.room_type_id = 5 THEN CASE WHEN d BETWEEN '2025-12-20' AND '2025-12-26' THEN 225 WHEN d BETWEEN '2025-12-27' AND '2026-01-06' THEN 300 WHEN d BETWEEN '2026-07-01' AND '2026-12-19' THEN 195 ELSE 150 END
        ELSE                          CASE WHEN d BETWEEN '2025-12-20' AND '2025-12-26' THEN 450 WHEN d BETWEEN '2025-12-27' AND '2026-01-06' THEN 600 WHEN d BETWEEN '2026-07-01' AND '2026-12-19' THEN 390 ELSE 300 END
    END,
    'PLN'
FROM rooms r,
     generate_series('2025-12-20'::date, '2026-12-19'::date, '1 day'::interval) d;

INSERT INTO reservations (hotel_id, room_id, guest_id, check_in_date, check_out_date, status, adults, children, total_amount) VALUES
    (1, 1,  1, '2025-11-01', '2025-11-04', 'checked_out', 1, 0,  240.00),
    (1, 3,  2, '2025-11-10', '2025-11-15', 'checked_out', 2, 0,  600.00),
    (1, 5,  3, '2025-12-01', '2025-12-05', 'checked_out', 2, 1, 1000.00),
    (2, 11, 4, '2025-11-20', '2025-11-23', 'checked_out', 2, 0,  270.00),
    (2, 13, 5, '2025-11-25', '2025-11-28', 'checked_out', 2, 0,  450.00),
    (1, 8,  6, '2025-12-10', '2025-12-14', 'cancelled',   2, 0,  480.00),
    (1, 3,  7, '2026-01-10', '2026-01-15', 'checked_in',  2, 0,  600.00),
    (2, 12, 8, '2026-01-08', '2026-01-12', 'checked_in',  2, 1,  360.00),
    (1, 5,  1, '2026-02-14', '2026-02-18', 'confirmed',   2, 0, 1000.00),
    (1, 9,  2, '2026-03-01', '2026-03-05', 'confirmed',   3, 0,  800.00),
    (2, 15, 3, '2026-03-10', '2026-03-15', 'confirmed',   2, 2, 1500.00),
    (1, 1,  4, '2026-04-01', '2026-04-03', 'pending',     1, 0,  160.00),
    (2, 14, 5, '2026-05-20', '2026-05-25', 'pending',     2, 0,  750.00),
    (1, 6,  6, '2026-06-15', '2026-06-20', 'confirmed',   2, 1, 1250.00),
    (2, 16, 7, '2026-07-01', '2026-07-07', 'confirmed',   2, 2, 2340.00);
