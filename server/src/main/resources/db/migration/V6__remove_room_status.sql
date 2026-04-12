-- Room status is now computed dynamically from today's reservations and room
-- blocks, so the stored column and its enum type are no longer needed.

ALTER TABLE rooms DROP COLUMN status;

DROP TYPE room_status;
