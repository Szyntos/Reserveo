-- Replace invoice FK with simple receipt fields on payments
ALTER TABLE payments DROP COLUMN invoice_id;
ALTER TABLE payments ADD COLUMN receipt_type   VARCHAR(10);
ALTER TABLE payments ADD COLUMN receipt_number VARCHAR(100);

-- Down payment requirement on reservations
ALTER TABLE reservations ADD COLUMN requires_down_payment BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reservations ADD COLUMN down_payment_amount   NUMERIC(10,2);

-- Remove invoices table (no longer needed)
DROP TABLE IF EXISTS invoices;
