# Workflow — Cập nhật tri thức (nghiệp vụ mới, PENDING được chốt, bug mới)

Chạy **ngay trong lượt** khi gặp trigger, không đợi cuối task. Đây là nguồn duy nhất cho "khi nào / ghi gì / ghi ở đâu"; các workflow khác chỉ trỏ về đây.

| Trigger | Ví dụ |
| --- | --- |
| T1. Thông tin nghiệp vụ mới / chốt PENDING / đổi quyết định | "Trường chốt suất lúc 9h", "GoKids có export Excel" |
| T2. Bug mới chưa có trong `ISSUE_INDEX` (kể cả chưa fix xong) | Exception lạ, số suất sai, lộ dữ liệu campus khác |
| T3. Edge case nghiệp vụ / hành vi bất ngờ của module | Trẻ chuyển lớp giữa tuần làm thống kê lệch |

## T1 — Nghiệp vụ mới / PENDING được chốt

1. **Nguồn:** ghi người nói, ngày, mức độ (CONFIRMED nếu từ trường/khảo sát/team chốt; PROPOSED nếu là đề xuất). Không tự nâng mức. Thông tin mơ hồ ⇒ hỏi lại 1 câu.
2. `docs/business/BUSINESS_RULES.md`: thêm/sửa rule (ID kế tiếp, status, nguồn). PENDING được chốt ⇒ chuyển dòng từ Pending register sang bảng **Đã đóng** (kết luận, ngày, nguồn).
3. **Grep mọi chỗ tham chiếu**, sửa từng hit (cả FE/APP nếu user cho phép, nếu không thì liệt kê cho user):
   `grep -rn "P-04\b" docs .ai ../CareNest_FE/docs ../CareNest_FE/.ai ../CareNest_APP/docs ../CareNest_APP/.ai`
4. Module card, flow, `GLOSSARY.md`, ADR còn OPEN liên quan. Quyết định kiến trúc mới ⇒ ADR mới (`ADR-TEMPLATE.md`).
5. Mâu thuẫn với rule CONFIRMED ⇒ **không ghi đè**; nêu mâu thuẫn, hỏi user.
6. Ảnh hưởng client ⇒ `docs/system/CROSS_REPO_MAP.md`. Trạng thái dự án đổi ⇒ `docs/context/CURRENT_STATE.md`.

## T2 — Bug mới

**Ghi khi** ít nhất một điều đúng: không hiển nhiên · phải thử >1 cách · liên module · có thể lặp lại · lỗi môi trường tốn >15 phút. Không ghi lỗi gõ nhầm.

1. Grep `docs/knowledge/ISSUE_INDEX.md` (chuỗi lỗi, module, từ khóa). Đã có ⇒ mở incident, bổ sung Attempts.
2. Chưa có ⇒ tạo ngay `docs/knowledge/incidents/<ID>.md` từ `_TEMPLATE.md` với `status: open` + 1 dòng `ISSUE_INDEX.md` (root cause `?`). ID: `BUG|CASE|ENV-YYMMDD-slug`.
3. Trong lúc điều tra: mỗi cách thử + kết quả ghi vào **Attempts** của incident, không chỉ trong câu trả lời, để phiên sau không mất.
4. Chứng minh root cause + fix ⇒ điền Root cause / Fix / Regression test, đổi status ở cả incident và index.
5. Bẫy của module ⇒ 1 dòng Known pitfalls trong module card. Liên module/repo ⇒ link ở `CROSS_MODULE_ISSUES.md`. Lỗi môi trường ⇒ thêm mục `TROUBLESHOOTING.md`. Bài học tổng quát ⇒ `PATTERNS.md`.
6. Không phải lỗi code mà do hiểu sai nghiệp vụ ⇒ xử lý như T1/T3.

## T3 — Edge case nghiệp vụ

Incident `CASE-YYMMDD-slug` (`type: edge-case`) + 1 dòng ISSUE_INDEX + Known pitfalls. Cần rule mới ⇒ T1 (PROPOSED cho tới khi được xác nhận).

## Quy tắc

- 1 dòng ở index, chi tiết ở file chi tiết. Link thay vì chép.
- Không ghi dữ liệu trẻ thật, secret, log dài.
- Không tạo incident cho chuyện chưa xảy ra.
- Báo user danh sách file tri thức đã cập nhật. Không commit khi user chưa yêu cầu.
