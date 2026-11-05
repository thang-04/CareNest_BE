# Data model — 012
Child and ChildGuardian gain nullable photo_key, photo_content_type, photo_size_bytes, photo_updated_at. Metadata is complete or all null; size 1..2097152, supported JPEG/PNG/WEBP MIME. New Flyway migration, no binary storage/backfill. PrivateImageStorage with children/photos/ prefix uses the existing bucket/client.

Upload index refers only to the same request guardian; persisted read uses stable guardian DB id and child ownership. Account photos remain separate fallback. Unrelated updates do not clear metadata. No new relationship/table.
