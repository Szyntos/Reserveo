CREATE TYPE app_role AS ENUM ('admin', 'user');

ALTER TABLE users
    ADD COLUMN app_role app_role NOT NULL DEFAULT 'user';
