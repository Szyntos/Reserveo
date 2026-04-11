CREATE TABLE IF NOT EXISTS price_rules (
    id                      SERIAL        PRIMARY KEY,
    room_id                 INTEGER       NOT NULL REFERENCES rooms(id),
    from_date               DATE          NOT NULL,
    to_date                 DATE          NOT NULL,
    min_nights              INTEGER       NOT NULL DEFAULT 1,
    max_nights              INTEGER,      -- NULL = no upper bound (5+ etc.)
    price_per_person_per_night DECIMAL(10,2) NOT NULL,
    currency                VARCHAR(10)   NOT NULL DEFAULT 'PLN',
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);
