-- payments table exists from V1; set defaults so UI can record payments
-- without requiring method/status to be specified explicitly
ALTER TABLE payments ALTER COLUMN method SET DEFAULT 'cash';
ALTER TABLE payments ALTER COLUMN status SET DEFAULT 'completed';
