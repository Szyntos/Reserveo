ALTER TABLE reservation_price_segments
    ADD COLUMN rule_id INTEGER REFERENCES price_rules(id) ON DELETE SET NULL;
