-- Rename receptionist → manager in hotel_user_role enum
ALTER TYPE hotel_user_role RENAME TO hotel_user_role_old;

CREATE TYPE hotel_user_role AS ENUM ('admin', 'manager', 'viewer');

ALTER TABLE user_hotel_roles
    ALTER COLUMN role TYPE hotel_user_role
    USING CASE role::text
        WHEN 'receptionist' THEN 'manager'::hotel_user_role
        ELSE role::text::hotel_user_role
    END;

DROP TYPE hotel_user_role_old;
