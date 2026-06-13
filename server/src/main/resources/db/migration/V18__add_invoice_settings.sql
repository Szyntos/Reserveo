CREATE TABLE invoice_settings (
    hotel_id                INTEGER PRIMARY KEY REFERENCES hotels(id),
    seller_name             VARCHAR(255),
    seller_address          TEXT,
    seller_nip              VARCHAR(20),
    seller_regon            VARCHAR(20),
    seller_bank_account     VARCHAR(50),
    seller_phone            VARCHAR(50),
    seller_email            VARCHAR(255),
    default_payment_method  VARCHAR(50) NOT NULL DEFAULT 'transfer',
    default_due_days        INTEGER NOT NULL DEFAULT 14
);
