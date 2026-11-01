# Analyze / design review — 01/11/2026

Phạm vi: domain spec, constitution CareNest 1.0.0, spec, plan quản trị/thiết kế, data model, contract, tasks và workflow user cung cấp.
Đây là bản ghi kết quả review read-only; không phải báo cáo implementation hay converge.

## Findings và xử lý trước code

| ID | Mức | Phát hiện | Xử lý |
| --- | --- | --- | --- |
| A1 | Medium | T016 dùng playwright.config.js, repo dùng .mjs | Sửa .mjs và dẫn đúng tests/e2e/system/edu-plan-flows.spec.mjs, year-plan-import.spec.mjs |
| A2 | Medium | T009/plan dẫn tên service test không có | Dùng EduClassDocServiceIntegrationTest.java hiện có |
| A3 | Low | Controller test chưa tồn tại nhưng task không nói tạo mới | T010 ghi rõ tạo MockMvc RED test mới |

Review độc lập và kiểm lại sau remediation: không còn lỗi chặn thiết kế; không có constitution conflict. Mã 7124–7126 trống tại thời điểm review.

## Coverage

| Requirement | Tasks |
| --- | --- |
| FR-001 | T003/004/007 |
| FR-002 | T008/012/014 |
| FR-003 | T007/009/014 |
| FR-004 | T009/010 |
| FR-005 | T009/013 |
| FR-006 | T013/014 |
| FR-007/008 | T015 |
| FR-009 | T008/009/010/013/014 |
| FR-010 | T003/004/007 |
| FR-011 | T004/005 |
| SC-001 | T007/016 |
| SC-002 | T009/016 |
| SC-003 | T013/014/016 |
| SC-004 | T015/016 |
| SC-005 | T003/004/016 |

11/11 FR và 5/5 SC có task. Tại thời điểm review trước code, 18 task đều pending và chưa chạy converge (đúng điều kiện chỉ chạy sau implement).
Workflow đã lưu đủ Domain spec 14 mục và chuỗi constitution → specify → clarify → plan → database design → tasks → analyze → implement → converge; giữ nguyên stack và cổng phê duyệt.

## Cổng còn chờ

User duyệt plan ba phase/endpoint và contract cụ thể trước code. Dependency và các quyết định nghiệp vụ Q1–Q4 đã được user duyệt; không xin lại các quyết định này.
Chưa chạy dependency resolution, test parser, integration hoặc E2E tính năng; không gán kết quả code từ review tài liệu.

## Cập nhật sau user duyệt

User đã duyệt plan và bắt đầu phase 1 ngày 01/11/2026; cổng phê duyệt trước code đã qua. Review code phase 1 độc lập không có P1/P2 chặn, kiểm 13 test parser/service đạt. Chưa chạy hội tụ toàn feature khi phase 2/3 còn chờ review/triển khai theo plan; không tuyên bố toàn feature đã sạch.

## Kết thúc toàn feature

User cho phép tiếp tục đến hoàn tất, không dừng giữa phase. Vòng hội tụ 1 tìm P1 allocation trong grid dùng chung, append-only T019, thực hiện RED/GREEN và rebuild. Vòng 2 review 11 FR/5 SC không còn gap; tasks hash trước/sau không đổi, không append gì khi sạch. Checklist implement cuối 19/19 hoàn thành, không xóa/đổi số task cũ.
Review lại xác nhận guard trước cấp phát và parity mock đã sửa, không còn P1/P2. Bằng chứng cuối: BE644/644, FE342/342, browser nhập nhánh3/3 + nhập mục tiêu17/17 trên jar cuối; hồi quy edu-flow19/19 ở lượt trước bản vá decoder. Xem quickstart để biết lượt thử lỗi, phạm vi và cleanup.

## Bổ sung 02/11 — tải mẫu và giao diện modal

Analyze FR-012/013 + SC-006 → T020/T021/T022: helper xuất mẫu có escape, không lấy draft/dates; caller truyền tuần học đã có; layout theo modal mục tiêu, giữ nguyên các handler đọc/áp dụng/xác nhận. Không API/DB/dependency mới, không đổi quyền. Không còn câu hỏi nghiệp vụ hoặc gap thiết kế chặn.
RED: 2 template tests thiếu helper và 2 modal tests còn size cũ/thiếu nút/cảnh báo chưa rút gọn. GREEN 4/4; full FE346/346. Browser component thực kiểm download Word, hàng nút, kích thước giống modal mục tiêu sau animation và mobile375, 0 lỗi JS. Hội tụ sau implement: FR-012/013, SC-006 đủ bằng chứng, không thêm task mới hoặc tiêu đề rỗng.
