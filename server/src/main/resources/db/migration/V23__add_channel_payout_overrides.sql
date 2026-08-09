-- Manual corrections to derived payout attribution.
--
-- By default a channel reservation is attributed to a payout month by the Thursday rule
-- (see PayoutAttribution.kt). When Booking's own report disagrees, a row here overrides it.
--
-- Sparse by design: only exceptions get a row, so untouched reservations stay purely derived
-- and keep re-attributing themselves when their dates change.
--
-- The table stores a *reassignment*, never an add/remove pair — a reservation cannot be
-- detached from every month by accident. The only way out of the partition is `excluded`,
-- which is an explicit, auditable decision rather than an oversight.

CREATE TABLE channel_payout_overrides (
    reservation_id INTEGER   PRIMARY KEY REFERENCES reservations(id),
    year           INTEGER,
    month          INTEGER   CHECK (month IS NULL OR month BETWEEN 1 AND 12),
    excluded       BOOLEAN   NOT NULL DEFAULT FALSE,
    reason         TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Either it is excluded, or it names a concrete month. Never neither.
    CONSTRAINT channel_payout_overrides_target CHECK (
        excluded OR (year IS NOT NULL AND month IS NOT NULL)
    )
);
