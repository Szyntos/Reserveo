-- Backfill and lock down the source_type column introduced in V1 but never populated.
UPDATE reservations SET source_type = 'private' WHERE source_type IS NULL;
ALTER TABLE reservations ALTER COLUMN source_type SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN source_type SET DEFAULT 'private';
