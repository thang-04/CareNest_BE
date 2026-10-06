# Workflow — Làm rõ nghiệp vụ (cổng trước khi code)

Bước 1 của `implement-feature`, `plan-change`, và `fix-bug` khi chưa rõ "hành vi đúng". Áp mọi làn khi task **đổi hành vi nghiệp vụ**. Chưa qua cổng ⇒ không code.

Làn S: 1 dòng `Rule: <ID> (<status>)`. Chưa CONFIRMED/ACCEPTED hoặc lệch tài liệu ⇒ nâng làn M, chạy đủ dưới đây.

1. **Hiểu nghiệp vụ** — block ≤8 dòng (làn L: ghi vào plan, mục "Làm rõ nghiệp vụ"):
   ```text
   Actor: … · Outcome: …
   Rule: ATT-05 (CONFIRMED) · P-03 (PENDING)        ← grep "| ATT-" BUSINESS_RULES.md, không đọc cả file
   Flow: [B] … → [S] …                               ← card + flow trong card
   Ảnh hưởng: module (MODULE_MAP) · scope/quyền · dữ liệu dẫn xuất (CMR) · FE/APP (CROSS_REPO_MAP) · dữ liệu cũ
   Tài liệu vs yêu cầu: <khớp | lệch: tài liệu ghi X (file:line), yêu cầu Y>
   ```
2. **Phân loại điểm chưa rõ:**
   - (a) **Nghiệp vụ** — rule chưa có / PENDING / OPEN, yêu cầu lệch tài liệu, ảnh hưởng mơ hồ ⇒ **bắt buộc hỏi user**.
   - (b) Kỹ thuật đã có convention (guide, `.claude/rules`) ⇒ tự làm theo, không hỏi.
   - (c) Kỹ thuật chưa có convention, ảnh hưởng lớn ⇒ đề xuất phương án rồi hỏi.
3. **Hỏi theo vòng** — mỗi vòng gộp ≤5 câu độc lập; hỏi từng câu chỉ khi câu sau phụ thuộc câu trước. Mỗi câu: phương án (trắc nghiệm) · đề xuất của agent + lý do · hệ quả · nguồn (`file:line`/rule ID). **Lặp tới khi hết câu (a).** Trả lời mơ hồ ⇒ hỏi tiếp, không tự diễn giải. "Làm cấu hình được" chỉ khi user nói rõ chưa chốt.
4. **Lệch tài liệu** ⇒ trình bày cả hai (tài liệu X `file:line` · yêu cầu Y), hỏi chọn. Chọn Y ⇒ cập nhật tài liệu (T1/ADR) trong cùng task. Không âm thầm theo bên nào.
5. **Ghi câu trả lời ngay** theo `update-knowledge.md` T1: rule mới/đổi + nguồn "user, ngày"; status theo đúng mức user nói (CONFIRMED chỉ khi team/trường đã chốt, còn lại PROPOSED). Mục đích: lần sau không hỏi lại.

Có ≥2 phương án thiết kế (làn M/L) ⇒ thêm bảng 2–3 phương án (trade-off, đề xuất trước), user chọn; làn L ghi vào "Key decisions" của plan.
