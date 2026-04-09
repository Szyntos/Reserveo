-- ─── Users ───────────────────────────────────────────────────────────────────

INSERT INTO users (name, email, created_at) VALUES
    ('Anna Kowalska',  'anna.kowalska@reserveo.dev', '2025-06-01 10:00:00'),
    ('Marek Nowak',    'marek.nowak@reserveo.dev',   '2025-06-01 10:05:00');

-- ─── Hotels ──────────────────────────────────────────────────────────────────

INSERT INTO hotels (name, address, phone, email, timezone, created_at) VALUES
    ('The Grand Palace', 'ul. Królewska 1, 00-001 Warszawa', '+48 22 100 2000', 'contact@grandpalace.pl', 'Europe/Warsaw', '2025-06-01 10:10:00'),
    ('Seaside Resort',   'ul. Plażowa 5, 81-001 Gdynia',     '+48 58 300 4000', 'info@seasideresort.pl',  'Europe/Warsaw', '2025-06-01 10:15:00');

-- ─── User → Hotel roles ───────────────────────────────────────────────────────

INSERT INTO user_hotel_roles (user_id, hotel_id, role) VALUES
    (1, 1, 'admin'),
    (1, 2, 'viewer'),
    (2, 1, 'receptionist'),
    (2, 2, 'admin');

-- ─── Room types ───────────────────────────────────────────────────────────────

INSERT INTO room_types (hotel_id, name, description) VALUES
    (1, 'Single',     'Cosy room for one guest'),
    (1, 'Double',     'Spacious room for two guests'),
    (1, 'Suite',      'Luxury suite with separate living area'),
    (2, 'Standard',   'Standard room with partial sea view'),
    (2, 'Deluxe',     'Deluxe room with balcony and sea view'),
    (2, 'Penthouse',  'Top-floor penthouse with panoramic sea view');

-- ─── Rooms ────────────────────────────────────────────────────────────────────
-- Hotel 1 (id=1): rooms 1-10   — type ids: Single=1, Double=2, Suite=3
-- Hotel 2 (id=2): rooms 11-20  — type ids: Standard=4, Deluxe=5, Penthouse=6

INSERT INTO rooms (hotel_id, room_type_id, number, floor, max_guests, status, description) VALUES
    (1, 1, '101', 1, 1, 'available', 'Single room, garden view'),
    (1, 1, '102', 1, 1, 'available', 'Single room, courtyard view'),
    (1, 1, '201', 2, 1, 'available', 'Single room, city view'),
    (1, 1, '202', 2, 1, 'available', 'Single room, city view'),
    (1, 2, '301', 3, 2, 'available', 'Double room, city view'),
    (1, 2, '302', 3, 2, 'available', 'Double room, city view'),
    (1, 2, '303', 3, 3, 'available', 'Double room with extra bed'),
    (1, 2, '304', 3, 3, 'available', 'Double room with extra bed'),
    (1, 3, '401', 4, 2, 'available', 'Junior suite with city panorama'),
    (1, 3, '402', 4, 4, 'available', 'Executive suite with living room'),
    (2, 4, '101', 1, 2, 'available', 'Standard room, partial sea view'),
    (2, 4, '102', 1, 2, 'available', 'Standard room, garden view'),
    (2, 4, '201', 2, 2, 'available', 'Standard room, sea view'),
    (2, 4, '202', 2, 2, 'available', 'Standard room, sea view'),
    (2, 5, '301', 3, 2, 'available', 'Deluxe room, sea view balcony'),
    (2, 5, '302', 3, 2, 'available', 'Deluxe room, sea view balcony'),
    (2, 5, '303', 3, 3, 'available', 'Deluxe room with extra bed'),
    (2, 5, '304', 3, 3, 'available', 'Deluxe room with extra bed'),
    (2, 6, '501', 5, 2, 'available', 'Penthouse with panoramic sea view'),
    (2, 6, '502', 5, 4, 'available', 'Penthouse with private terrace');

-- ─── Guests ───────────────────────────────────────────────────────────────────

INSERT INTO guests (first_name, last_name, email, phone, nationality, blacklisted, created_at) VALUES
    ('Jan',     'Wiśniewski',  'jan.wisniewski@gmail.com',   '+48 601 111 001', 'PL', FALSE, '2025-07-01 09:00:00'),
    ('Maria',   'Dąbrowska',   'maria.dabrowska@gmail.com',  '+48 601 111 002', 'PL', FALSE, '2025-07-02 09:00:00'),
    ('Thomas',  'Müller',      'thomas.mueller@gmail.com',   '+49 151 111 003', 'DE', FALSE, '2025-08-01 09:00:00'),
    ('Sophie',  'Dupont',      'sophie.dupont@gmail.com',    '+33 6 11 11 004', 'FR', FALSE, '2025-08-05 09:00:00'),
    ('Piotr',   'Zielski',     'piotr.zielski@gmail.com',    '+48 601 111 005', 'PL', FALSE, '2025-09-01 09:00:00'),
    ('Elena',   'Rossi',       'elena.rossi@gmail.com',      '+39 333 111 006', 'IT', FALSE, '2025-09-10 09:00:00'),
    ('Adam',    'Lewandowski', 'adam.lewandowski@gmail.com', '+48 601 111 007', 'PL', FALSE, '2025-10-01 09:00:00'),
    ('Natalia', 'Wójcik',      'natalia.wojcik@gmail.com',   '+48 601 111 008', 'PL', FALSE, '2025-10-15 09:00:00');

-- ─── Room night prices ────────────────────────────────────────────────────────
-- Base price per person per night (PLN):
--   Single (type 1): 80   Double (type 2): 120   Suite (type 3): 250
--   Standard (type 4): 90  Deluxe (type 5): 150   Penthouse (type 6): 300
--
-- Season multipliers:
--   Christmas  20 Dec 2025 – 26 Dec 2025 : ×1.5
--   New Year   27 Dec 2025 –  6 Jan 2026 : ×2.0
--   Regular     7 Jan 2026 – 30 Jun 2026 : ×1.0
--   Summer      1 Jul 2026 – 19 Dec 2026 : ×1.3

INSERT INTO room_night_prices (room_id, date, price_per_person, currency)
SELECT
    r.id,
    d::date,
    ROUND(
        CASE r.room_type_id
            WHEN 1 THEN 80
            WHEN 2 THEN 120
            WHEN 3 THEN 250
            WHEN 4 THEN 90
            WHEN 5 THEN 150
            WHEN 6 THEN 300
        END
        *
        CASE
            WHEN d::date BETWEEN '2025-12-20' AND '2025-12-26' THEN 1.5
            WHEN d::date BETWEEN '2025-12-27' AND '2026-01-06' THEN 2.0
            WHEN d::date BETWEEN '2026-01-07' AND '2026-06-30' THEN 1.0
            WHEN d::date BETWEEN '2026-07-01' AND '2026-12-19' THEN 1.3
        END,
        2
    ),
    'PLN'
FROM rooms r,
     generate_series('2025-12-20'::date, '2026-12-19'::date, '1 day'::interval) d;

-- ─── Reservations ─────────────────────────────────────────────────────────────
-- Room map (for reference):
--   Hotel 1 → 101=1 102=2 201=3 202=4 | 301=5 302=6 303=7 304=8 | 401=9 402=10
--   Hotel 2 → 101=11 102=12 201=13 202=14 | 301=15 302=16 303=17 304=18 | 501=19 502=20

-- Past — checked_out
INSERT INTO reservations (
    hotel_id, room_id, guest_id, created_by,
    source_type, check_in_date, check_out_date, check_in_time, check_out_time,
    status, adults, children, currency,
    subtotal_amount, deduction_amount, extra_fee_amount, total_amount,
    deposit_status, created_at
) VALUES
    -- Jan Wiśniewski — Hotel 1, room 101 (Single), 10–14 Jan 2026, 4 nights
    (1, 1, 1, 1, 'private', '2026-01-10', '2026-01-14',
     '2026-01-10 15:00:00', '2026-01-14 11:00:00',
     'checked_out', 1, 0, 'PLN', 320.00, 0, 0, 320.00, 'not_required', '2026-01-05 10:00:00'),

    -- Maria Dąbrowska — Hotel 1, room 301 (Double), 1–5 Feb 2026, 4 nights × 2 pax
    (1, 5, 2, 2, 'private', '2026-02-01', '2026-02-05',
     '2026-02-01 14:00:00', '2026-02-05 11:00:00',
     'checked_out', 2, 0, 'PLN', 960.00, 0, 0, 960.00, 'not_required', '2026-01-20 12:00:00'),

    -- Thomas Müller — Hotel 2, room 301 (Deluxe), 27 Dec–3 Jan (New Year), 7 nights × 2 pax
    (2, 15, 3, 1, 'external', '2025-12-27', '2026-01-03',
     '2025-12-27 16:00:00', '2026-01-03 11:00:00',
     'checked_out', 2, 0, 'PLN', 4200.00, 0, 0, 4200.00, 'paid', '2025-12-01 09:00:00'),

    -- Sophie Dupont — Hotel 2, room 501 (Penthouse), 20–27 Dec (Christmas), 7 nights × 2 pax
    (2, 19, 4, 2, 'external', '2025-12-20', '2025-12-27',
     '2025-12-20 15:00:00', '2025-12-27 11:00:00',
     'checked_out', 2, 1, 'PLN', 6300.00, 0, 0, 6300.00, 'paid', '2025-11-15 11:00:00'),

    -- Piotr Zielski — Hotel 1, room 401 (Suite), 1–5 Mar 2026, 4 nights × 2 pax
    (1, 9, 5, 1, 'private', '2026-03-01', '2026-03-05',
     '2026-03-01 15:00:00', '2026-03-05 11:00:00',
     'checked_out', 2, 0, 'PLN', 2000.00, 0, 0, 2000.00, 'not_required', '2026-02-20 10:00:00'),

    -- Elena Rossi — Hotel 1, room 201 (Single), cancelled
    (1, 3, 6, 2, 'private', '2026-02-15', '2026-02-18',
     NULL, NULL,
     'cancelled', 1, 0, 'PLN', 240.00, 0, 0, 240.00, 'not_required', '2026-02-01 10:00:00');

-- Currently checked in (reference date: 2026-04-09)
INSERT INTO reservations (
    hotel_id, room_id, guest_id, created_by,
    source_type, check_in_date, check_out_date, check_in_time,
    status, adults, children, currency,
    subtotal_amount, deduction_amount, extra_fee_amount, total_amount,
    deposit_status, created_at
) VALUES
    -- Adam Lewandowski — Hotel 2, room 201 (Standard), 8–12 Apr 2026
    (2, 13, 7, 1, 'private', '2026-04-08', '2026-04-12',
     '2026-04-08 15:00:00',
     'checked_in', 2, 0, 'PLN', 360.00, 0, 0, 360.00, 'not_required', '2026-04-01 10:00:00'),

    -- Natalia Wójcik — Hotel 1, room 302 (Double), 7–11 Apr 2026
    (1, 6, 8, 2, 'private', '2026-04-07', '2026-04-11',
     '2026-04-07 14:00:00',
     'checked_in', 2, 1, 'PLN', 480.00, 0, 0, 480.00, 'not_required', '2026-03-25 11:00:00');

-- Future — confirmed / pending
INSERT INTO reservations (
    hotel_id, room_id, guest_id, created_by,
    source_type, check_in_date, check_out_date,
    status, adults, children, currency,
    subtotal_amount, deduction_amount, extra_fee_amount, total_amount,
    deposit_status, created_at
) VALUES
    -- Jan Wiśniewski — Hotel 1, room 201 (Single), 10–15 May 2026, 5 nights
    (1, 3, 1, 1, 'private', '2026-05-10', '2026-05-15',
     'confirmed', 1, 0, 'PLN', 400.00, 0, 0, 400.00, 'not_required', '2026-04-01 09:00:00'),

    -- Elena Rossi — Hotel 1, room 303 (Double), 25–30 May 2026, 5 nights × 2 pax
    (1, 7, 6, 2, 'private', '2026-05-25', '2026-05-30',
     'pending', 2, 1, 'PLN', 600.00, 0, 0, 600.00, 'not_required', '2026-04-07 09:30:00'),

    -- Sophie Dupont — Hotel 2, room 302 (Deluxe), 20–25 Jun 2026, 5 nights × 2 pax
    (2, 16, 4, 2, 'external', '2026-06-20', '2026-06-25',
     'confirmed', 2, 0, 'PLN', 1500.00, 0, 0, 1500.00, 'pending', '2026-04-05 14:00:00'),

    -- Thomas Müller — Hotel 2, room 501 (Penthouse), 1–8 Jul 2026 (summer), 7 nights × 2 pax
    (2, 19, 3, 1, 'external', '2026-07-01', '2026-07-08',
     'confirmed', 2, 0, 'PLN', 5460.00, 200.00, 0, 5260.00, 'paid', '2026-03-15 10:00:00'),

    -- Maria Dąbrowska — Hotel 1, room 401 (Suite), 10–17 Aug 2026, 7 nights × 2 pax
    (1, 9, 2, 1, 'private', '2026-08-10', '2026-08-17',
     'pending', 2, 2, 'PLN', 4550.00, 0, 0, 4550.00, 'not_required', '2026-04-08 10:00:00'),

    -- Adam Lewandowski — Hotel 2, room 303 (Deluxe), 5–10 Sep 2026, 5 nights × 2 pax
    (2, 17, 7, 1, 'private', '2026-09-05', '2026-09-10',
     'confirmed', 2, 0, 'PLN', 1950.00, 0, 0, 1950.00, 'paid', '2026-04-08 11:00:00'),

    -- Piotr Zielski — Hotel 1, room 402 (Suite), 15–20 Nov 2026, 5 nights × 2 pax
    (1, 10, 5, 2, 'private', '2026-11-15', '2026-11-20',
     'pending', 2, 0, 'PLN', 1625.00, 0, 0, 1625.00, 'not_required', '2026-04-09 08:00:00');
