-- Monthly OTA (Booking.com) payouts.
--
-- A channel reservation's stored payments record what the *guest* was charged, which is
-- not what the hotel receives: the channel keeps a commission and settles in bulk. This
-- table records the money actually transferred, one row per hotel per calendar month.
--
-- Which reservations a payout covers is NOT stored — it is derived from check-out dates
-- (see PayoutAttribution.kt), because Booking pays every Thursday and a reservation is
-- paid out in the first Thursday transfer strictly after its check-out. A stay ending
-- 28.06 is therefore settled on 02.07 and belongs to July's payout, not June's.

CREATE TABLE channel_payouts (
    id          SERIAL PRIMARY KEY,
    hotel_id    INTEGER     NOT NULL REFERENCES hotels(id),
    year        INTEGER     NOT NULL,
    month       INTEGER     NOT NULL CHECK (month BETWEEN 1 AND 12),
    amount      DECIMAL(10,2) NOT NULL,
    currency    VARCHAR(10) NOT NULL DEFAULT 'PLN',
    notes       TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT channel_payouts_unique_month UNIQUE (hotel_id, year, month)
);

CREATE INDEX idx_channel_payouts_hotel ON channel_payouts(hotel_id, year, month);
