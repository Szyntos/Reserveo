-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE room_status AS ENUM (
    'available', 'occupied', 'cleaning', 'maintenance', 'out_of_service'
);

CREATE TYPE hotel_user_role AS ENUM (
    'admin', 'receptionist', 'viewer'
);

CREATE TYPE reservation_status AS ENUM (
    'pending', 'confirmed', 'checked_in', 'checked_out', 'cancelled', 'no_show'
);

CREATE TYPE reservation_source_type AS ENUM (
    'private', 'external'
);

CREATE TYPE deposit_status AS ENUM (
    'not_required', 'pending', 'paid', 'forfeited'
);

CREATE TYPE pricing_adjustment_type AS ENUM (
    'deduction', 'surcharge'
);

CREATE TYPE pricing_adjustment_reason AS ENUM (
    'discount', 'coupon', 'manual_discount', 'cleaning_fee',
    'city_tax', 'service_fee', 'late_checkout', 'other'
);

CREATE TYPE invoice_status AS ENUM (
    'draft', 'issued', 'paid', 'overdue', 'cancelled'
);

CREATE TYPE payment_method AS ENUM (
    'cash', 'card', 'bank_transfer', 'online'
);

CREATE TYPE payment_status AS ENUM (
    'pending', 'completed', 'failed', 'refunded'
);

-- ─── Hotels ───────────────────────────────────────────────────────────────────

CREATE TABLE hotels (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR NOT NULL,
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
