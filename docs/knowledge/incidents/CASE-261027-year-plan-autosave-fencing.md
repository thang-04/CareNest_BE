---
id: CASE-261027-year-plan-autosave-fencing
type: edge-case
modules: [education-plan]
rules: [EDU-10, EDU-22, EDU-23]
status: fixed
date: 2026-10-27
keywords: [autosave, revision, offline, recovery, editing-session]
similar_to: []
---

# Tự lưu cần phân biệt phiên, bản nội dung và bản phục hồi

## Symptom

Khi triển khai tự lưu, review làn L phát hiện các nhánh có thể mất dữ liệu: tab cùng tài khoản dùng chung bản phục hồi; thao tác trang chi tiết lấy revision mới rồi ghi nội dung cũ; tiếp tục gõ trong lúc ban hành; lấy khóa hoàn tất sau khi đóng trang.

## Attempts

| Lần | Cách | Kết quả |
| --- | --- | --- |
| 1 | Giữ bản phục hồi chung user/plan | Tab không giữ quyền có thể ghi đè bản cũ; không đủ an toàn |
| 2 | Chỉ cho tab đã từng giữ quyền ghi backup | Hai tab từng giữ quyền vẫn đua khi tab cũ offline; chưa đủ |
| 3 | Bản phục hồi theo user/plan/slot, giữ revision caller, khóa nhập khi chuyển trạng thái, nhả late-acquire theo generation | Đã triển khai và thêm ca kiểm; cần gate cuối và browser smoke |

## Root cause

Quyền theo tài khoản không phân biệt hai tab; nhận quyền sửa không đồng nghĩa đã đọc bản nội dung mới. Nội dung đã giữ trên máy và nội dung đã xác nhận trên server là hai trạng thái khác nhau.

## Fix

V30 (trước merge daily-attendance là V24, trước merge `dev` là V27) thêm phiên/revision/request ID; mọi ghi kiểm phiên/revision. Web queue tuần tự, giữ keystroke mới khi ánh xạ ID; retry cùng request ID; service phục hồi lưu mỗi phiên riêng, không tự ghi đè khi nối mạng lại.

## Regression test

EduYearPlanServiceIntegrationTest thêm ca hai tab, stale revision, replay, expiry 90s và mục tiêu rỗng. FE year-plan-autosave/year-plan-api/year-plan-recovery kiểm nhịp lưu, retry, ID và cách ly bản phục hồi. Chưa đóng incident trước khi chạy gate cuối.

## Lesson

Không tự nâng expected revision bằng bản trả về từ lấy khóa nếu caller vẫn đang giữ nội dung cũ. Bản phục hồi phải có định danh phiên và chỉ xóa nguồn sau khi bản thay thế đã được bảo toàn.

## Kiểm tra bổ sung

- Queue sau lỗi 400 từng giữ snapshot bị từ chối mãi; test RED tái hiện, sửa để lấy snapshot mới sau validation error, giữ ID cũ chỉ cho retry mạng/5xx.
- PostgreSQL CHECK cho phép UNKNOWN: editing_from=NULL từng lọt điều kiện. Schema test RED trên PostgreSQL tái hiện; V24 mới (nay V30; chưa commit) thêm IS NOT NULL, chờ gate GREEN.
- Browser mock smoke xác nhận tự lưu, bàn giao /new→/edit và một phiên sửa giữa hai tab; sau đóng phiên giữ quyền, tab còn lại nhận quyền qua nút thử lại. Không coi đây là browser E2E với BE thật.

## Kết quả kiểm chứng 27/10/2026

BE `VERIFY PASS full | tests 380 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; FE verify chuẩn đạt sau sửa fixture Date. Bộ giáo dục gồm ca queue/retry/validation/offline mô phỏng, recovery và API stale revision; browser mock xác nhận tự lưu, bàn giao quyền, hai tab và mở lại có bản phục hồi. Không coi đây là kiểm chứng browser trên backend thật hoặc SLA mạng production.
