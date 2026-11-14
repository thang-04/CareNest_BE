-- HLT-21: khoảng thời gian đã phân tích xu hướng (bộ lọc biểu đồ 6 tháng / 12 tháng / toàn bộ); bản cũ phân tích toàn bộ.
ALTER TABLE health_interpretations ADD COLUMN period VARCHAR(20) NOT NULL DEFAULT 'ALL';
ALTER TABLE health_interpretations ADD CONSTRAINT ck_health_interpretations_period
    CHECK (period IN ('MONTHS_6', 'MONTHS_12', 'ALL'));

-- HLT-21: GV duyệt nhận định xu hướng ⇒ báo phụ huynh (HEALTH_TREND_APPROVED)
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED', 'HEALTH_TREND_APPROVED',
    'CHILD_PLACED', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR'));
