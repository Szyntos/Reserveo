-- Change adults from integer to decimal to support fractional values (e.g. 3.5 = adult + child)
ALTER TABLE reservations ALTER COLUMN adults TYPE NUMERIC(4,1) USING adults::NUMERIC(4,1);

-- Remove children column — absorbed into fractional adults
ALTER TABLE reservations DROP COLUMN children;

-- Add per-reservation description/notes field
ALTER TABLE reservations ADD COLUMN description TEXT;

-- Remove email from guests — not needed for hotel ops
ALTER TABLE guests DROP COLUMN email;
