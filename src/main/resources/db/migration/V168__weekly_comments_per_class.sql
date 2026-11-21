-- Giữ nhận xét lớp cũ và cho lớp nhận trẻ viết riêng trong cùng tuần.
ALTER TABLE weekly_comments DROP CONSTRAINT uk_weekly_comments_child_week;
ALTER TABLE weekly_comments ADD CONSTRAINT uk_weekly_comments_child_class_week
    UNIQUE (child_id, classroom_id, week_start);
