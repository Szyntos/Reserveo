CREATE TABLE invoices (
    id                  SERIAL PRIMARY KEY,
    hotel_id            INTEGER NOT NULL REFERENCES hotels(id),
    reservation_id      INTEGER REFERENCES reservations(id),
    invoice_number      VARCHAR(50)  NOT NULL,
    issue_date          DATE         NOT NULL,
    sale_date           DATE         NOT NULL,
    due_date            DATE         NOT NULL,
    payment_method      VARCHAR(50)  NOT NULL DEFAULT 'transfer',
    seller_name         VARCHAR(255) NOT NULL,
    seller_address      TEXT,
    seller_nip          VARCHAR(20),
    seller_regon        VARCHAR(20),
    seller_bank_account VARCHAR(50),
    seller_phone        VARCHAR(50),
    seller_email        VARCHAR(255),
    buyer_name          VARCHAR(255) NOT NULL,
    buyer_address       TEXT,
    buyer_nip           VARCHAR(20),
    total_net           DECIMAL(10,2) NOT NULL,
    total_vat           DECIMAL(10,2) NOT NULL,
    total_gross         DECIMAL(10,2) NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE invoice_items (
    id             SERIAL PRIMARY KEY,
    invoice_id     INTEGER NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    ordinal        INTEGER NOT NULL,
    name           VARCHAR(500) NOT NULL,
    quantity       DECIMAL(10,3) NOT NULL,
    unit           VARCHAR(50)  NOT NULL DEFAULT 'szt.',
    unit_net_price DECIMAL(10,2) NOT NULL,
    vat_rate       VARCHAR(10)  NOT NULL,
    net_amount     DECIMAL(10,2) NOT NULL,
    vat_amount     DECIMAL(10,2) NOT NULL,
    gross_amount   DECIMAL(10,2) NOT NULL
);
