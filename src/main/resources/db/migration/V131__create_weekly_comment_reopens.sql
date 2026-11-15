-- Mở lại nhận xét tuần đã gửi (OBS-29, Cần chốt 14/11/2026): HT hoặc PHT có quyền mở lại đánh giá ở điểm trường của lớp
-- đưa bản đã gửi về nháp để GV sửa rồi gửi lại; lưu lịch sử ai mở, lúc nào, lý do và nội dung đã gửi trước đó.
CREATE TABLE weekly_comment_reopens (
    id                BIGSERIAL PRIMARY KEY,
    weekly_comment_id BIGINT        NOT NULL REFERENCES weekly_comments (id),
    reopened_by       BIGINT        NOT NULL REFERENCES user_accounts (id),
    reopened_at       TIMESTAMPTZ   NOT NULL,
    reason            VARCHAR(300)  NOT NULL,
    previous_content  TEXT          NOT NULL,
    previous_sent_at  TIMESTAMPTZ   NOT NULL,
    previous_sent_by  BIGINT        NOT NULL REFERENCES user_accounts (id),
    CONSTRAINT ck_weekly_comment_reopens_reason CHECK (char_length(btrim(reason)) > 0)
);

CREATE INDEX idx_weekly_comment_reopens_comment ON weekly_comment_reopens (weekly_comment_id);
CREATE INDEX idx_weekly_comment_reopens_at ON weekly_comment_reopens (reopened_at DESC);
