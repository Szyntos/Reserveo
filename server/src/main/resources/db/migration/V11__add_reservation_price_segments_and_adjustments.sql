-- Drop old unused pricing tables
DROP TABLE IF EXISTS room_night_prices;
DROP TABLE IF EXISTS reservation_price_adjustments;

-- Drop old enum types that were only used in the old reservation_price_adjustments
DROP TYPE IF EXISTS pricing_adjustment_type;
DROP TYPE IF EXISTS pricing_adjustment_reason;

-- Baked price segments per reservation (from_date..to_date with a fixed ppn × adults)
CREATE TABLE reservation_price_segments (
    id                         SERIAL PRIMARY KEY,
    reservation_id             INTEGER        NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    from_date                  DATE           NOT NULL,
    to_date                    DATE           NOT NULL,
    price_per_person_per_night NUMERIC(10, 2) NOT NULL,
    adults                     NUMERIC(4, 1)  NOT NULL,
    currency                   VARCHAR(10)    NOT NULL DEFAULT 'PLN'
);

-- Manual price adjustments per reservation (discounts, surcharges, etc.)
CREATE TABLE reservation_price_adjustments (
    id             SERIAL PRIMARY KEY,
    reservation_id INTEGER        NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    amount         NUMERIC(10, 2) NOT NULL,
    description    VARCHAR(255)
);
