-- The init script already created tags(id, name) and room_tags(room_id, tag_id).
-- We need to add hotel_id to tags and re-scope the uniqueness per hotel.

ALTER TABLE tags ADD COLUMN IF NOT EXISTS hotel_id INTEGER;

-- Infer hotel_id for any existing tag assignments from their room's hotel
UPDATE tags t
SET hotel_id = (
    SELECT r.hotel_id
    FROM room_tags rt
    JOIN rooms r ON r.id = rt.room_id
    WHERE rt.tag_id = t.id
    LIMIT 1
);

-- Drop any orphan tags that have no room assignment (no way to infer hotel)
DELETE FROM tags WHERE hotel_id IS NULL;

ALTER TABLE tags ALTER COLUMN hotel_id SET NOT NULL;

-- Replace global unique-name constraint with per-hotel unique constraint
ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_name_key;
ALTER TABLE tags ADD CONSTRAINT tags_hotel_id_name_key UNIQUE (hotel_id, name);

-- Migrate comma-separated tags from rooms.tags column (added by V15)
INSERT INTO tags (hotel_id, name)
SELECT DISTINCT r.hotel_id, trim(t.tag)
FROM rooms r
CROSS JOIN LATERAL unnest(string_to_array(r.tags, ',')) AS t(tag)
WHERE trim(t.tag) <> ''
ON CONFLICT (hotel_id, name) DO NOTHING;

INSERT INTO room_tags (room_id, tag_id)
SELECT r.id, tg.id
FROM rooms r
CROSS JOIN LATERAL unnest(string_to_array(r.tags, ',')) AS t(tag)
JOIN tags tg ON tg.hotel_id = r.hotel_id AND tg.name = trim(t.tag)
WHERE trim(t.tag) <> '';

ALTER TABLE rooms DROP COLUMN tags;
