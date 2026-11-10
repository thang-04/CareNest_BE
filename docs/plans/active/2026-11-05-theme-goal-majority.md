---
title: Tính mục tiêu cuối chủ đề theo 50% lần đánh giá
status: in-progress
owner: Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [learning-observation]
rules: [OBS-27, OBS-28]
risk: medium
created: 2026-11-05
updated: 2026-11-05
---
# Tính mục tiêu cuối chủ đề theo 50% lần đánh giá

## Context
ThemeEvaluationServiceImpl.java:353 lấy lần chấm cuối; người dùng yêu cầu thay bằng tỷ lệ ít nhất 50% lần đã chấm để kết luận từng mục tiêu. FE ThemeEvaluationPage.jsx:238 đang mô tả lần chấm cuối. Phiếu năm dùng lại kết quả chủ đề qua childThemeResults.

## Quyết định đã chốt
- Cần dẫn quy tắc cô ở trường và yêu cầu sửa; chốt tính từng hoạt động đã chấm ngày 05/11/2026.
- Chưa chấm/vắng không tính mẫu số, không lần nào thì trống. Lưu sửa cùng hoạt động chỉ thay kết quả.
- Giữ tổng kết cả chủ đề 70%, sửa tay và bảng đã xác nhận; không migration, không đổi DTO/API, không sửa APP, không commit/push.

## Làm rõ nghiệp vụ
Actor GV/PHT/HT; OBS-27 đổi từ lần cuối sang tỷ lệ từng hoạt động, OBS-28 nhận kết quả mới khi phiếu chưa chốt. Giữ phạm vi lớp/ngày chủ đề, nối mục tiêu qua dòng gốc. Một dòng hoạt động chỉ tính mục tiêu một lần dù có nhiều ID cùng dòng gốc; kết quả lỗi chồng lấn ưu tiên chưa đạt, không tăng mẫu số. Kiểm tra điểm danh hiện tại để bỏ dữ liệu kỹ năng còn lại trên ngày đã đổi sang vắng.

### Câu hỏi mở
Không còn: user đã chọn tính từng hoạt động cô chấm.

## Acceptance criteria
- AC-1 (OBS-27): một đạt/một chưa đạt là đạt, một đạt/hai chưa đạt là chưa đạt, hai đạt/một chưa đạt là đạt.
- AC-2 (OBS-27): không đánh giá/vắng/ngoài chủ đề/khác lớp không tính; lưu sửa không thêm lượt; cùng mục tiêu gốc trong hoạt động không đếm trùng.
- AC-3 (OBS-27): ô sửa tay ưu tiên và bảng xác nhận giữ nguyên; tổng kết 70% giữ nguyên.
- AC-4 (OBS-28): phiếu năm chưa chốt nhận kết quả mới; phiếu đã chốt giữ nguyên.
- AC-5: FE mô tả đúng 50% theo hoạt động; không tính lại nghiệp vụ trên FE.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Đơn vị | Hoạt động đã chấm | Gộp theo ngày | User đã chốt, không bỏ mất lần quan sát |
| Kết quả | Đếm số nguyên đạt*2 >= tổng | Trung bình phần trăm đã làm tròn | Giữ chính xác biên 50% |

## Phases
### Phase 1 — Tính, hiển thị và kiểm chứng
- Sửa service, integration test, OBS-27 và module card; FE sửa hướng dẫn bảng. Không thay schema/DTO.
- Test AC-1..4 trong ThemeEvaluationIntegrationTest, test phạm vi ngày và lưu sửa; FE giữ test render/confirm hiện có.
- Exit: node scripts/verify.mjs ở BE và FE; node scripts/check-ai-layer.mjs cho tài liệu. Skip không coi là đã kiểm chứng.
- Review bằng subagent theo workflow; dừng chờ user review khi hoàn tất.

## Test matrix
| AC | Test | Kết quả |
| --- | --- | --- |
| AC-1..4 | ThemeEvaluationIntegrationTest | 8/8 đạt, không skip |
| AC-5 | FE verify và rà hướng dẫn | VERIFY PASS full |

## Rủi ro & rollback
Dữ liệu nháp có thể đổi dấu khi kết quả trước đây dựa vào lần cuối. Bảng đã xác nhận không tính lại. Rollback từng hunk của task, không đụng application.yml hay SQL local.

## Validation log
Không đổi contract, không tính tổng hợp ở FE; có test biên và downstream. User duyệt phạm vi sửa qua chuỗi trao đổi và chốt đơn vị hoạt động.

## Progress log
### 2026-11-05 — Phase 1
- Cần chốt “oki bạn làm theo tính từng hoạt động cô chấm”; triển khai theo phạm vi trên.

### 2026-11-05 — Phase 1 chờ review
- BE verify full: 1041 tests, fail 0, error 0, skip 0; archunit/snapshot/spotless đạt, 661s. FE VERIFY PASS full. Nhóm cuối chủ đề 8/8 PostgreSQL thật đạt.
- Review read-only không blocker production; đã sửa fixture JPA cache, enum điểm danh, tăng cường test freeze.
- Không sửa schema/DTO/APP hoặc form học kỳ; không commit/push.
