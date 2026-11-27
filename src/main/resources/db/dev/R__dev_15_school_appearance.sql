-- Tranh nền chung của trường: mặc định Tự động, bật cả bộ tranh; không ghi đè khi Hiệu trưởng đã lưu.
INSERT INTO school_appearance (id, mode, pinned_scene_id, enabled_scene_ids)
VALUES (1, 'AUTO', 'T0',
        ARRAY['T0', 'T1', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'T8', 'T9', 'T10', 'T11', 'T12', 'T13', 'T14', 'T15', 'T16',
              'S1', 'S3', 'S8'])
ON CONFLICT (id) DO NOTHING;
