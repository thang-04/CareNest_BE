-- Phiếu đánh giá cuối năm (OBS-28, Cần yêu cầu 06/11/2026): nhận xét riêng dưới từng lĩnh vực, không bắt buộc.
-- JSON {"<domainId>": "nhận xét"} (lĩnh vực chưa gắn mã dùng khóa "0"); chốt cùng phiếu khi xác nhận đánh giá cuối năm.
ALTER TABLE child_evaluations
    ADD COLUMN domain_comments TEXT;
