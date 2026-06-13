CREATE TABLE holidays (
    id       SERIAL PRIMARY KEY,
    hotel_id INTEGER      NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    name     VARCHAR(255) NOT NULL,
    from_date DATE         NOT NULL,
    to_date   DATE         NOT NULL
);
