# Domain spec — Biên bản kiểm thực ba bước (M1–M3), sổ lưu mẫu (M4, M5) và chữ ký ảnh

Trạng thái: **draft**, chờ user duyệt. Plan quản trị: `docs/plans/active/2026-11-10-inspection-records-signatures.md`. Phần client Web: `CareNest_FE:docs/plans/active/2026-11-09-food-sample-records-signatures.md` (đã có màn chạy bằng dữ liệu mẫu, là bản đối chiếu giao diện và contract đề xuất).

## 1. Domain Overview

Nhà trường phải lưu hồ sơ **kiểm thực ba bước** hằng ngày theo Quyết định 1246/QĐ-BYT (Bước 1 trước chế biến — Mẫu 1; Bước 2 khi chế biến — Mẫu 2; Bước 3 trước khi ăn — Mẫu 3) và **sổ lưu mẫu thức ăn** (Mẫu 5, nhãn Mẫu 4) để thanh tra. PMS (qlmn.vn, xem chỉ đọc 09/11/2026) chỉ cho nhập bảng trên trang in, ký bằng họ tên gõ chữ, không khóa sau ký, không lịch sử. CareNest cho PHT điền các mẫu trên Web, ký bằng **ảnh chữ ký** do chính người ký tải lên, lưu kèm biên bản, xuất In/PDF/Excel.

Hiện BE có: thực đơn công bố, suất đã xác nhận, kiểm nhận hàng tươi hai lần, phiếu nhập kho khô theo lô (nhà cung cấp, hạn dùng), sổ lưu mẫu (NUT-21: ghi, PHT duyệt, hủy sau 24 giờ) và ảnh bữa ăn. BE **chưa có**: kiểm thực M1–M3, chữ ký ảnh, các trường lưu mẫu theo Mẫu 5 (khối lượng/thể tích, dụng cụ chứa, nhiệt độ bảo quản).

## 2. Scope

**Trong phạm vi**
- Thư viện chữ ký ảnh của tài khoản (tải, đặt mặc định, xóa, tối đa 5).
- Biên bản kiểm thực theo ngày–điểm trường–mẫu: M1 tươi, M1 khô, M2, M3: khung dòng do hệ thống dựng, PHT nhập ô còn lại, lưu nháp, ký, khóa sau ký, sửa lại có lý do, lịch sử, bản chụp dòng lúc ký.
- Mẫu M5 (soát sổ lưu mẫu) cùng cơ chế ký; bản in M3 gộp, M4 do client lập từ dữ liệu.
- Mở rộng bản ghi lưu mẫu: khối lượng/thể tích, dụng cụ chứa, nhiệt độ bảo quản theo từng món (bếp ghi trên App).

**Ngoài phạm vi (V1)**
- Chữ ký số có giá trị pháp lý (ảnh chữ ký chỉ là hình thức thay họ tên gõ chữ trong hồ sơ nội bộ).
- Bếp và nhân viên y tế/thanh tra ký ảnh (ô ký tay sau khi in); duyệt lưu mẫu/hủy mẫu kèm chữ ký ảnh (làm sau khi user chốt).
- Kiểm thực đa trường, báo cáo thống kê kiểm thực, nhắc việc.

## 3. Actors & Roles

| Actor | Việc | Mã quyền (có sẵn) |
| --- | --- | --- |
| PHT dinh dưỡng của điểm trường | Điền, lưu nháp, ký, sửa lại M1–M3, M5; tải chữ ký của mình | `FOOD_SAFETY_APPROVE` (theo điểm trường, HT cấp) — giả định, chờ user chốt |
| Hiệu trưởng | Như PHT, mọi điểm trường; xem | `FOOD_SAFETY_APPROVE` mặc định |
| Bếp (tài khoản chung mỗi cơ sở, AUTH-22) | Ghi lưu mẫu trên App kèm khối lượng, dụng cụ, nhiệt độ; không dùng Web (AUTH-21) | `KITCHEN_RECORD` |
| GV, phụ huynh, Admin | Không truy cập biên bản kiểm thực | — |

## 4. Domain Concepts

- **Chữ ký tài khoản** (`UserSignature`): ảnh PNG/JPG của một tài khoản, có cờ mặc định, tối đa 5.
- **Biên bản kiểm thực** (`InspectionSheet`): một mẫu của một điểm trường trong một ngày; có trạng thái, phiên bản, phần đầu (thời gian, địa điểm kiểm tra).
- **Dòng biên bản** (`InspectionRow`): một dòng theo khóa (thực phẩm hoặc bữa–món), gồm giá trị hệ thống điền sẵn và ô PHT nhập.
- **Chữ ký trên biên bản** (`InspectionSignature`): bản **sao** ảnh chữ ký lúc ký, người ký, giờ ký, phiên bản biên bản.
- **Lịch sử biên bản** (`InspectionEvent`): ký, sửa lại kèm lý do.
- **Bản chụp dòng** (`snapshot`): các dòng lúc ký, để chứng từ không đổi khi dữ liệu nguồn đổi.

## 5. Domain Relationships

Điểm trường 1—n Biên bản (ngày × mẫu duy nhất). Biên bản 1—n Dòng. Biên bản 1—0..1 Chữ ký hiệu lực, 1—n Lịch sử. Tài khoản 1—n Chữ ký tài khoản; Chữ ký trên biên bản **không** tham chiếu chữ ký tài khoản (đã chép ảnh). Dòng M1 tham chiếu thực phẩm; dòng M2/M3/M5 tham chiếu bữa và món. Dữ liệu nguồn: kiểm nhận hàng tươi, phiếu nhập kho và lô, thực đơn công bố, suất xác nhận, bản ghi lưu mẫu.

## 6. User Requirements

1. PHT mở biên bản của một ngày và thấy sẵn món, bữa, số suất, hàng nhập, nhà cung cấp, hạn dùng.
2. PHT điền các ô còn lại, lưu nháp (cả tự lưu), tích Đạt / Không đạt cả cột, điền chung một cột.
3. PHT ký bằng ảnh chữ ký của mình khi đã chọn đủ Đạt / Không đạt mọi dòng (M5: không bắt buộc cột này).
4. Mẫu đã ký bị khóa; sửa lại cần lý do, chữ ký cũ vào lịch sử, ký lại sau khi sửa.
5. PHT xem lại, In, xuất PDF/Excel bất cứ lúc nào.
6. Bếp ghi thêm khối lượng/thể tích, dụng cụ chứa, nhiệt độ cho từng món lưu mẫu trên App.

## 7. Business Rules

Rule mới/đổi, **đều PROPOSED, chờ user duyệt** (chưa ghi vào `BUSINESS_RULES.md`):

| Mã đề xuất | Nội dung |
| --- | --- |
| NUT-21 (sửa) | Bếp ghi lưu mẫu trên App kèm khối lượng/thể tích, dụng cụ chứa, nhiệt độ; PHT duyệt (như cũ); **chữ ký lấy ảnh trong hệ thống cho các biên bản PHT ký**, ô của bếp và nhân viên y tế/thanh tra vẫn ký tay trên bản in. Thay câu "lấy chữ ký trên giấy ngoài hệ thống" |
| P-26 (đóng) | Mẫu sổ lưu mẫu theo Mẫu 4 và Mẫu 5 của QĐ 1246/QĐ-BYT như PMS |
| P-27 (đóng) | Kiểm thực ba bước ghi trên hệ thống; PHT điền M1–M3; M1 điền sẵn từ kiểm nhận hàng tươi và phiếu nhập kho |
| INS-01 (mới) | Mỗi (điểm trường, ngày, mẫu) có tối đa một biên bản; PHT có `FOOD_SAFETY_APPROVE` ở điểm trường đó (HT mọi điểm) mới ghi/ký/sửa lại; người khác trong Web chỉ xem nếu được đọc |
| INS-02 (mới) | Ký chỉ khi biên bản đã lưu và, với M1–M3, mọi dòng đã chọn Đạt hoặc Không đạt; người ký phải có chữ ký trong tài khoản |
| INS-03 (mới) | Ký chép ảnh chữ ký vào biên bản và chụp các dòng; biên bản đã ký bị khóa, bản sao ảnh và bản chụp không đổi dù tài khoản đổi/xóa chữ ký hay dữ liệu nguồn đổi |
| INS-04 (mới) | "Sửa lại" cần lý do (≥3 ký tự), mọi ngày được; chữ ký hiện tại vào lịch sử, biên bản về chưa ký và dựng lại dòng từ dữ liệu nguồn mới nhất |
| SIG-01 (mới) | Mỗi tài khoản tối đa 5 chữ ký; một mặc định; ảnh PNG/JPG ≤2 MB, nhận diện theo byte đầu; chỉ chủ tài khoản và người xem biên bản chứa ảnh đó tải được nội dung, không URL công khai |

## 8. Business Processes

1. **Chuẩn bị chữ ký:** PHT vào "Chữ ký của tôi", tải ảnh, đặt mặc định.
2. **Điền biên bản:** mở ngày/mẫu → hệ thống dựng khung dòng → PHT nhập, lưu nháp (nhiều lần).
3. **Ký:** chọn chữ ký → hệ thống kiểm đủ điều kiện → chép ảnh, chụp dòng → trạng thái Đã ký, ghi lịch sử.
4. **Sửa lại:** nhập lý do → mở khóa → sửa → ký lại.
5. **Xuất:** In/PDF/Excel lấy dữ liệu đã lưu (client dựng); ảnh chữ ký tải qua API có xác thực.
6. **Lưu mẫu (bếp, App):** bếp ghi/sửa bản lưu mẫu kèm khối lượng, dụng cụ, nhiệt độ; PHT duyệt/hủy như NUT-21.

## 9. State / Lifecycle

Biên bản: `DRAFT` → (ký) `SIGNED` → (sửa lại) `DRAFT` (chữ ký vào lịch sử) → ký lại … Chưa có bản ghi = chưa nhập gì (khung do hệ thống dựng). Không xóa biên bản. Chữ ký tài khoản: tạo → (mặc định) → xóa.

## 10. Business Constraints

- Khóa duy nhất (điểm trường, ngày, mẫu); phiên bản tăng mỗi lần nội dung đổi hoặc sửa lại.
- Ngày không sau hôm nay cho ghi mới (khớp NUT-21); xem ngày tương lai chỉ có khung.
- Ảnh chữ ký không vào log, không vào fixture thật (nguyên tắc 6).
- Biên bản đã ký không sửa trực tiếp; mọi sửa đổi đi qua "Sửa lại" và để lại lịch sử.

## 11. Non-Functional Requirements

- Tải khung ngày (4–5 mẫu) dưới 1 giây với dữ liệu hiện có; tải ảnh chữ ký qua API có token, `Cache-Control: private`.
- Quyền kiểm ở server theo điểm trường; thiếu quyền ⇒ mã lỗi riêng, không lộ tồn tại bản ghi của điểm trường khác.
- Ghi đồng thời (hai người cùng lưu một mẫu): khóa dòng điểm trường/biên bản, người sau nhận lỗi xung đột có thông điệp tiếng Việt.

## 12. Acceptance Criteria

- **AC-1**: Given ngày có thực đơn công bố · When `GET /inspections/day` · Then bốn mẫu M1–M3 có dòng đúng thực đơn, suất xác nhận, hàng nhập.
- **AC-2**: Given biên bản nháp · When lưu rồi đọc lại · Then ô nhập giữ nguyên, trạng thái chưa ký.
- **AC-3**: Given thiếu Đạt / Không đạt ở một dòng M1–M3 · When ký · Then 422, không đổi trạng thái.
- **AC-4**: Given đủ điều kiện · When ký · Then lưu bản sao ảnh, người ký, giờ ký, bản chụp dòng, lịch sử `SIGNED`; sửa trực tiếp sau đó trả 409.
- **AC-5**: Given biên bản đã ký · When sửa lại không lý do · Then 422; có lý do · Then về nháp, lịch sử `REOPENED` kèm lý do.
- **AC-6**: Given tài khoản không có quyền ở điểm trường · When đọc/ghi/ký · Then 403.
- **AC-7**: Given tài khoản đổi hoặc xóa chữ ký sau khi ký · When đọc biên bản · Then ảnh trên biên bản không đổi.
- **AC-8**: Given tải chữ ký sai loại/quá cỡ/quá 5 · Then từ chối đúng mã lỗi; ảnh chỉ tải được qua API có xác thực của chủ hoặc người được xem biên bản.
- **AC-9**: Given bản ghi lưu mẫu cũ thiếu các trường mới · When đọc · Then trả rỗng, không lỗi.

## 13. Domain Terminology

Kiểm thực ba bước; Mẫu 1–5; QĐ 1246/QĐ-BYT; M1 tươi/khô; cảm quan; lưu mẫu, hủy mẫu; PHT dinh dưỡng; bản chụp dòng; sửa lại.

## 14. Assumptions & Open Questions

Đã trả lời bởi user ngày 2026-11-09/10 (qua phiên làm việc FE): PHT điền M1–M3, bếp ghi lưu mẫu; ký ảnh thay họ tên; xác nhận đổi NUT-21 và đóng P-26, P-27; khóa sau ký, sửa lại mọi ngày có lý do; ký cần đủ cảm quan.

Giả định cần user soát:
- PHT dùng mã quyền hiện có `FOOD_SAFETY_APPROVE`, không thêm mã mới.
- Bếp và nhân viên y tế/thanh tra ký tay trên bản in (không ký ảnh) trong V1.
- Ảnh chữ ký không phải chữ ký số có giá trị pháp lý.
- Biên bản lưu trong một bảng với cột jsonb (ADR-0024) theo hướng ERD gọn của nhánh bán trú.

Câu hỏi đã chốt 2026-11-10 (user trả lời):
- [x] Bộ rule INS-01..04, SIG-01 được duyệt và đã ghi vào `BUSINESS_RULES.md` (CONFIRMED).
- [x] `fresh_checks` thêm `supplier_id`: M1 tươi điền sẵn nhà cung cấp từ kiểm nhận.
- [x] Số suất ở M2/M3/M5 tách Nhà trẻ / Mẫu giáo như PMS (FE đang dùng số tổng, đổi theo khi nối API).
- [x] Làm trên nhánh bán trú `feature/G94-254-BE-FEAT-27-day-boarding`.

Câu hỏi còn mở (không chặn):
- [ ] Mã Jira riêng cho task này trước khi commit.
