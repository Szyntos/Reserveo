-- Split the single phone column into country_code + phone_number
ALTER TABLE guests RENAME COLUMN phone TO phone_number;
ALTER TABLE guests ADD COLUMN country_code VARCHAR(10);
