# Workflow — Cập nhật tri thức (nghiệp vụ mới, PENDING được chốt, bug mới)

Chạy **ngay trong lượt** khi gặp một trong các trigger dưới, không đợi cuối task. Mục tiêu: lần sau agent khác không phải hỏi lại hoặc điều tra lại.

## Trigger

| Trigger | Ví dụ |
| --- | --- |
| T1. User đưa thông tin nghiệp vụ mới / chốt một mục PENDING / đổi quyết định | "Trường chốt suất lúc 9h", "phụ huynh được xem chiều cao cân nặng", "GoKids có export Excel" |
| T2. Gặp **bug mới** (chưa có trong `ISSUE_INDEX`) — kể cả khi chưa fix xong | Exception lạ, số suất sai, lộ dữ liệu campus khác, test fail không rõ lý do |
| T3. Gặp edge case nghiệp vụ / hành vi bất ngờ của module | Trẻ chuyển lớp giữa tuần làm thống kê lệch |

## T1 — Nghiệp vụ mới / PENDING được chốt

1. Ghi nhận nguồn: ai nói, ngày (hôm nay), mức độ (CONFIRMED nếu từ trường/khảo sát; PROPOSED nếu ý kiến/đề xuất). **Không tự nâng mức.** Thông tin mơ hồ ⇒ hỏi lại 1 câu trước khi ghi.
2. `docs/business/BUSINESS_RULES.md`: thêm/sửa rule (ID kế tiếp trong nhóm, status, nguồn). Mục PENDING được chốt ⇒ đổi status rule bị chặn + **xóa dòng khỏi Pending register** (ghi "chốt <ngày>: <nội dung>" vào cột nguồn của rule).
3. Module card liên quan (`docs/modules/`): cập nhật Rules / PENDING / Known pitfalls.
4. Flow (`docs/business/flows/`) nếu bước nghiệp vụ đổi; `GLOSSARY.md` nếu có thuật ngữ mới.
5. ADR liên quan còn OPEN/PENDING ⇒ cập nhật status + phần "Còn chờ". Quyết định kiến trúc mới ⇒ ADR mới theo `ADR-TEMPLATE.md`.
6. Thông tin mâu thuẫn với rule CONFIRMED ⇒ **không ghi đè**: nêu mâu thuẫn, hỏi user cái nào đúng.
7. Ảnh hưởng client (hiển thị, quyền, contract) ⇒ ghi vào `docs/system/CROSS_REPO_MAP.md` hoặc nêu cho user việc FE/APP cần cập nhật.
8. `docs/context/CURRENT_STATE.md` nếu trạng thái dự án đổi.

## T2 — Bug mới

1. Grep `docs/knowledge/ISSUE_INDEX.md` (chuỗi lỗi, module, từ khóa). Đã có ⇒ dùng incident đó, bổ sung Attempts mới nếu có.
2. Chưa có và **chưa rõ root cause** ⇒ thêm 1 dòng `docs/knowledge/KNOWN_ISSUES.md` (triệu chứng, chuỗi lỗi nguyên văn, module, ngày, trạng thái "đang điều tra"). Không tạo incident khi chưa chứng minh root cause.
3. Trong lúc điều tra: mỗi cách thử + kết quả ghi tạm trong câu trả lời/ghi chú, để chuyển vào incident.
4. Khi đã chứng minh root cause và fix ⇒ theo `fix-bug.md` bước 7: incident `incidents/<ID>-<slug>.md` (kèm Attempts thất bại), 1 dòng `ISSUE_INDEX.md`, xóa dòng tương ứng ở `KNOWN_ISSUES.md`, Known pitfalls trong module card, `PATTERNS.md` nếu tổng quát hóa được.
5. Bug liên module hoặc liên repo ⇒ thêm/link ở `CROSS_MODULE_ISSUES.md`.
6. Bug do người dùng hiểu sai nghiệp vụ (không phải lỗi code) ⇒ xử lý như T1/T3.

## T3 — Edge case nghiệp vụ

`CASE-xxx` incident nếu đáng nhớ (theo template, `type: edge-case`) + 1 dòng `ISSUE_INDEX` + Known pitfalls module card. Nếu cần rule mới ⇒ T1 (status PROPOSED cho tới khi được xác nhận).

## Quy tắc

- Ghi ngắn, đúng chỗ (1 dòng index, chi tiết trong file chi tiết); không nhân bản cùng một thông tin ra nhiều file — link thay vì chép.
- Không ghi dữ liệu trẻ thật, secret, log dài.
- Báo cho user danh sách file tri thức đã cập nhật. Không commit nếu user chưa yêu cầu.
