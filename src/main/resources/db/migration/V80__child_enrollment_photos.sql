ALTER TABLE children
    ADD COLUMN photo_key VARCHAR(36),
    ADD COLUMN photo_content_type VARCHAR(30),
    ADD COLUMN photo_size_bytes INTEGER,
    ADD COLUMN photo_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_children_photo CHECK (
        (photo_key IS NULL AND photo_content_type IS NULL AND photo_size_bytes IS NULL AND photo_updated_at IS NULL)
        OR (photo_key IS NOT NULL AND photo_content_type IN ('image/jpeg', 'image/png', 'image/webp')
            AND photo_content_type IS NOT NULL AND photo_size_bytes BETWEEN 1 AND 2097152
            AND photo_size_bytes IS NOT NULL AND photo_updated_at IS NOT NULL)
    );

ALTER TABLE child_guardians
    ADD COLUMN photo_key VARCHAR(36),
    ADD COLUMN photo_content_type VARCHAR(30),
    ADD COLUMN photo_size_bytes INTEGER,
    ADD COLUMN photo_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_child_guardians_photo CHECK (
        (photo_key IS NULL AND photo_content_type IS NULL AND photo_size_bytes IS NULL AND photo_updated_at IS NULL)
        OR (photo_key IS NOT NULL AND photo_content_type IN ('image/jpeg', 'image/png', 'image/webp')
            AND photo_content_type IS NOT NULL AND photo_size_bytes BETWEEN 1 AND 2097152
            AND photo_size_bytes IS NOT NULL AND photo_updated_at IS NOT NULL)
    );
