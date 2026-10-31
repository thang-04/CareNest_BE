# Tasks: Nhập file mục tiêu năm học

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

## BE

- [x] T001 ApiCode 7033 `GOAL_FILE_UNSUPPORTED`, 7034 `GOAL_FILE_NO_TABLE`, 7035 `GOAL_FILE_TOO_LARGE`
- [x] T002 DTO `GoalFilePreviewResponse` (domains → rows → contents/themes, themeAbbrs, warnings)
- [x] T003 Đọc lưới ô: `.docx` (word/document.xml, `gridSpan`, `vMerge`), `.xlsx` (sharedStrings, sheet1, `mergeCells`), `.doc` HTML (`rowspan/colspan`), CSV; XML an toàn, giới hạn giải nén
- [x] T004 Diễn giải bảng: tìm tiêu đề, bố cục Thái Dương / CSV 6 cột, dải lĩnh vực / lĩnh vực con / nhóm, mục tiêu gộp, nội dung, dấu tích, cảnh báo theo dòng
- [x] T005 Service `EduGoalFileService.preview(user, file)` kiểm quyền (Phó HT hoặc GOALS_EDIT), dung lượng, đuôi file
- [x] T006 Endpoint `POST /education-plans/goals/file-preview` (multipart) + OpenAPI snapshot
- [x] T007 Unit test diễn giải (docx/xlsx/html/csv dựng trong test) + integration test endpoint (quyền, lỗi định dạng)
- [x] T008 BUSINESS_RULES EDU-46 (PROPOSED – chờ Tiền duyệt), module card nếu có

## FE

- [x] T009 API + facade `previewGoalFile(file)`; mock đọc CSV/HTML bằng bộ diễn giải JS
- [x] T010 `models/education-plan/importGoalFile.mjs` `mergeFileGoals` + node test
- [x] T011 `goalTemplateFile(themes)` (Word bảng A trống với dòng lĩnh vực và cột chủ đề)
- [x] T012 `ImportGoalFileModal.jsx` (Tải file mẫu · chọn file · xem trước · cảnh báo · Thêm/Hủy) nối vào nút/menu "Nhập file"; bỏ đọc CSV cũ ở trang
- [x] T013 Playwright BE thật: nhập `.docx` dựng trong test (mục tiêu, nội dung, dấu tích, viết tắt lạ), Hủy không đổi bảng, Lưu rồi đối chiếu BE; điện thoại mở từ menu

## Gate

- [x] T014 BE `node scripts/verify.mjs`, FE `node scripts/verify.mjs`, Playwright kế hoạch giáo dục đủ bộ
- [ ] T015 Commit BE/FE, push, cập nhật PR vào `dev`
