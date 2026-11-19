-- Spec 005 FR-012 (DB-F, Tiền duyệt 11/11/2026): bỏ hẳn "Kế hoạch điều chỉnh và bổ sung"; V96 đã đổi ràng buộc _week thành _day
ALTER TABLE edu_class_docs DROP CONSTRAINT ck_edu_class_docs_adjustments, DROP CONSTRAINT ck_edu_class_docs_adjustments_day, DROP COLUMN adjustments;
