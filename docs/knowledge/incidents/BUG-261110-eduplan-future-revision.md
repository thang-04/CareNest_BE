---
id: BUG-261110-eduplan-future-revision
type: bug
modules: [education-plan]
rules: [EDU-64]
status: "fixed; browser verification pending"
date: 2026-11-10
keywords: [future-edit, dossier, publication, history]
---

# BUG-261110 — sửa phần tương lai của dossier ban hành

## Nguyên nhân

saveClassDoc và permissions kiểm editable() toàn hồ sơ, chặn cả phần tương lai của APPROVED. Nhánh là tài liệu cả năm nên không thể chuyển trạng thái mọi chủ đề cùng lúc. FE tự gắn MT khi gõ có thể vô tình ghi picks vào ô quá khứ.

## Sửa

EDU-64: kiểm phần thay đổi theo ngày ở EduClassDocFutureEdit; khóa hôm nay/quá khứ và phần dùng chung tuần đã bắt đầu. Server đối chiếu vị trí ngày với weekStart, không tin date client. Reopen theo themeId trong cùng giao dịch/khóa plan; chỉ dossier liên quan về DRAFT, REVIEW bị từ chối. Lưu previousApprovals/previousPrintSnapshot trong history trước khi dọn metadata hoàn tất. Nhánh nháp thiếu ngày dùng ngày nguồn để kiểm an toàn, không tự điền ngày vào dữ liệu.

FE đọc canEdit, khóa ô ngày đã tới, refresh cả dossier sau lưu nhánh/tuần/chủ đề; tự gắn MT nhận predicate ô được sửa. Mock cùng quy tắc. Không thay contract hoặc migration.

## Kiểm chứng

EduClassDocFutureEditTest kiểm ngày hiện tại, quá khứ, ngày client giả, trường chung tuần, nhánh chưa hoàn chỉnh và phạm vi chủ đề. EduWorkflowServiceIntegrationTest kiểm save thật, resubmit, khóa REVIEW, giữ lịch sử bản ban hành, stale version và chủ nhiệm; ma trận 21 role/status bảo toàn TEAM khi từ chối xóa. FE class-doc-future-edit.test.mjs kiểm fake backend và tự liên kết MT. Xem báo cáo workspace `huong_dan_nghiep_vu/ket_qua_trien_khai_task_ke_hoach_giao_duc_2026-11-10.md` cho output cuối. Chưa browser/E2E hoặc DB runtime.
