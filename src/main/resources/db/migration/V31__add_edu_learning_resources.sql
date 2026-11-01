CREATE TABLE edu_learning_resources (
    id BIGSERIAL PRIMARY KEY,
    class_doc_id BIGINT NOT NULL REFERENCES edu_class_docs(id),
    activity_key VARCHAR(180),
    activity_signature VARCHAR(64),
    activity_label VARCHAR(1000) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0),
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    upload_request_id UUID NOT NULL,
    uploaded_by BIGINT NOT NULL REFERENCES user_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_learning_resource_request UNIQUE (class_doc_id, upload_request_id),
    CONSTRAINT ck_learning_resource_binding CHECK (
        (activity_key IS NULL AND activity_signature IS NULL) OR
        (activity_key IS NOT NULL AND activity_signature IS NOT NULL AND length(activity_signature)=64))
);
CREATE INDEX ix_learning_resources_doc ON edu_learning_resources(class_doc_id);
