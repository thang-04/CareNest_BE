# Bán trú — menu, luồng, màn hình, ma trận quyền

Bổ sung cho `meal-management.md` (luồng) và `BUSINESS_RULES.md` (NUT-*, ATT-*, AUTH-*). Viết 2026-11-02 sau khi user chốt công bố thực đơn cả tuần. Mọi quyền chạy trên cơ chế có sẵn: quyền hiệu lực = phần cố định của role ∪ (ma trận ∪ GRANT − REVOKE), GRANT mang phạm vi điểm trường/cả trường (AUTH-10, AUTH-20, ADR-0017, ADR-0023); phạm vi campus/lớp/trẻ luôn kiểm ở BE (AUTH-07, AUTH-13).

**Ký hiệu trạng thái:** **C** = đã xác nhận từ rule có ID (CONFIRMED/ACCEPTED). **P** = agent đề xuất 02/11, cần user/trường xác minh. **B** = việc ngoài hệ thống. Mã quyền mới (`MENU_MANAGE`…) đều là đề xuất thiết kế (P); hành vi nghiệp vụ đứng sau ghi C/P riêng.

**Ký hiệu phạm vi:** T = toàn trường · "điểm trường" = điểm trường được phân công, hoặc cả trường nếu HT cấp phạm vi `SCHOOL` · L = lớp đang phân công · Tr = trẻ có liên kết · TK = điểm trường của tài khoản bếp.

## 1. Menu Web (PHT, HT)

| Nhóm | Mục | Ghi chú |
| --- | --- | --- |
| Bán trú hôm nay | (một trang) | Việc của ngày theo điểm trường: suất đã chốt chưa, kho thiếu, kiểm nhận, mẫu chờ duyệt, ảnh bữa, thực đơn tuần sau đã công bố chưa |
| Thực đơn & dinh dưỡng | Kế hoạch tuần & công bố · Bộ thực đơn mẫu · Món thay thế dị ứng | Cân đối dinh dưỡng là tab trong kế hoạch tuần, không thành mục riêng |
| Vận hành bếp | Sổ suất ăn · Bếp hôm nay (xem như bếp thấy) · Bản dự trù hàng tươi · Kiểm nhận hàng tươi | "Sổ suất ăn" chuyển từ nhóm Điểm danh sang đây cho PHT/HT (xác nhận suất là việc mảng dinh dưỡng — NUT-02) |
| Kho thực phẩm | Tồn kho khô theo lô · Phiếu nhập · Lịch sử nhập/trừ · Phần thiếu chờ bổ sung | Chỉ kho khô; hàng tươi không cộng kho (NUT-12) |
| An toàn thực phẩm | Duyệt lưu/hủy mẫu · In nhãn & biểu mẫu · Ảnh bữa | |
| Danh mục & thiết lập | Thực phẩm · Món · Cấu hình nhóm tuổi · Nhà cung cấp · Khung giờ ảnh bữa (chỉ HT) | |

App: **bếp** — Hôm nay, Lưu mẫu, Ảnh bữa. **GV** — điểm danh (báo ăn) như hiện có; xem thực đơn tuần (P). **Phụ huynh** — Thực đơn tuần + ảnh bữa. Bếp không có Web (AUTH-21).

## 2. Business flow xuyên suốt

| # | Bước | Khi nào | Ai · kênh | Màn hình | Đầu vào | Đầu ra | Rule · TT |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Lập & công bố thực đơn | Tuần trước | PHT dinh dưỡng · Web | Kế hoạch tuần & công bố | Bộ mẫu 4 tuần / lập tay / AI nháp; món + định lượng; cấu hình nhóm tuổi; số trẻ dị ứng theo chất (không tên) | Thực đơn tuần PUBLISHED cả trường; món thay thế dị ứng; cảnh báo kho theo sĩ số dự kiến | NUT-08 (C, chốt 02/11), NUT-13, NUT-18..20, NUT-23, NUT-11 (C) |
| 1b | Bản dự trù, đặt hàng tươi | Trước kỳ | PHT · Web; gửi NCC (B) | Bản dự trù hàng tươi | Thực đơn tuần; sĩ số dự kiến điền sẵn theo nhóm tuổi × điểm trường | Bản dự trù đã lưu, danh sách đặt hàng in | NUT-07, NUT-12 (C) |
| 2 | Báo ăn | Sáng, trước giờ chốt | GV · App; PH gửi đơn nghỉ · App | Điểm danh | Trẻ đang học trong lớp; đơn nghỉ | Điểm danh (có mặt = ăn mọi bữa của nhóm tuổi) | ATT-01, ATT-02, ATT-04, ATT-06 (C) |
| 3 | Chốt suất | Giờ chốt | Hệ thống tổng hợp; PHT điểm trường (HT thay) xác nhận · Web | Sổ suất ăn | Điểm danh đã khóa | Số suất điểm trường × ngày × bữa × lớp CONFIRMED | NUT-01..04 (C) |
| 4 | Tính nguyên liệu | Ngay khi xác nhận | Hệ thống | Bán trú hôm nay · Kho | Thực đơn tuần đã công bố × suất đã xác nhận + thải bỏ | Nhu cầu ngày theo điểm trường; bút toán trừ kho khô (lô hết hạn trước); phần thiếu | NUT-07 (C), NUT-11 (C, P-38 duyệt 02/11) |
| 5 | Tiếp nhận | Sáng (tươi); khi mua (khô) | Bếp kiểm, báo PHT (B); PHT ghi · Web | Kiểm nhận hàng tươi · Phiếu nhập | Bản dự trù; nhu cầu theo suất; phiếu giao NCC | Kiểm nhận lần 1, lần 2 (đủ/thiếu/thừa); lô kho khô mới, trừ tiếp phần thiếu | NUT-11, NUT-12 (C) |
| 6 | Chế biến | Trong ngày | Bếp · App (xem); nấu (B) | Bếp hôm nay (App) | Thực đơn ngày, suất lớp × bữa, định lượng tổng, suất thay thế theo món × lớp | Không ghi trạng thái nấu; lưu mẫu ghi App; ảnh bữa trong khung giờ | AUTH-05, NUT-16, NUT-21, NUT-23 (C); NUT-14 REMOVED |
| 7 | Bàn giao | Giờ ăn | Bếp chia suất theo danh sách lớp (B) | Bếp hôm nay: bảng suất theo lớp | Suất đã xác nhận theo lớp | Không ghi nhận; thiếu/thừa báo PHT qua Zalo | NUT-15 REMOVED, NUT-22 (C) |
| 8 | Lưu hồ sơ | ≥24 giờ sau lấy mẫu; cuối kỳ | Bếp ghi hủy · App; PHT duyệt, in · Web | Duyệt lưu/hủy mẫu · In biểu mẫu · Lịch sử kho | Bản ghi lưu/hủy mẫu | Nhãn, biểu mẫu lưu/hủy (ký giấy — B); lịch sử suất, kho, ảnh lưu `StorageService` | NUT-21 (C); mẫu sổ P-26 |

## 3. Chức năng từng màn hình

| Màn hình (kênh) | Chức năng | Đầu vào | Đầu ra | Người dùng |
| --- | --- | --- | --- | --- |
| Bán trú hôm nay (Web) | Danh sách việc theo điểm trường × ngày, mỗi việc một nút tới màn xử lý | Suất, kho, kiểm nhận, mẫu, ảnh, trạng thái thực đơn tuần sau | — (chỉ đọc) | PHT, HT |
| Kế hoạch tuần & công bố (Web) | Lưới ngày × bữa × khối; sao tuần từ bộ mẫu; AI nháp; kiểm tự động; tab cân đối; công bố cả tuần | Món, cấu hình nhóm tuổi, số trẻ dị ứng theo chất | Tuần DRAFT → PUBLISHED | PHT có `MENU_MANAGE`; người khác xem |
| Bộ thực đơn mẫu (Web) | Bộ 4 tuần có nhãn mùa, theo khối nhà trẻ / mẫu giáo | Món | Bộ mẫu | như trên |
| Món thay thế dị ứng (Web) | Bảng chất × món → món thay thế; hệ thống gợi ý món không chứa chất | Món, chất dị ứng | Cặp thay thế dùng chung | như trên |
| Sổ suất ăn (Web) | Xem/xác nhận/xác nhận lại theo điểm trường × bữa × lớp | Điểm danh đã khóa | Suất CONFIRMED, lịch sử | PHT điểm trường, HT |
| Bếp hôm nay (App bếp; Web xem) | Thực đơn ngày, suất lớp × bữa, định lượng tổng điểm trường, suất thay thế theo món × lớp, nhãn "Mới" khi xác nhận lại | Thực đơn tuần, suất | — | Bếp (TK); PHT/HT xem |
| Bản dự trù (Web) | Chọn kỳ, điểm trường, thực đơn; sĩ số dự kiến điền sẵn, sửa; in danh sách đặt hàng | Thực đơn tuần, sĩ số đang học | Bản dự trù | PHT có `STOCK_MANAGE` |
| Kiểm nhận hàng tươi (Web) | Bảng thực phẩm × (theo dự trù, theo suất, thực nhận, kết quả) | Bản dự trù, nhu cầu theo suất | Kiểm nhận 2 lần | như trên |
| Kho khô (Web) | Tồn theo lô; phiếu nhập (NCC, lô, hạn, giá); lịch sử nhập/trừ; phần thiếu chờ bổ sung | Phiếu giao NCC | Lô, bút toán | như trên |
| Duyệt lưu/hủy mẫu, In (Web) | Hàng chờ duyệt; in nhãn mẫu, biểu mẫu từ bản ghi đã duyệt | Bản ghi của bếp | Bản ghi đã duyệt, file in | PHT có `FOOD_SAFETY_APPROVE` |
| Lưu mẫu (App bếp) | Ghi lưu (ngày, bữa, món điền sẵn từ thực đơn ngày); ghi hủy sau ≥24 giờ; xem trạng thái duyệt | Thực đơn ngày | Bản ghi chờ duyệt | Bếp (TK) |
| Ảnh bữa (App bếp; Web, App PH xem) | Chụp/sửa ảnh mỗi bữa trong khung giờ | Ảnh | Ảnh theo điểm trường × ngày × bữa | Bếp đăng; PHT, HT, PH xem |
| Danh mục & thiết lập (Web) | Thực phẩm (nguồn dinh dưỡng bắt buộc), món + gam/trẻ theo nhóm tuổi, cấu hình nhóm tuổi có ngày hiệu lực, NCC; HT: khung giờ ảnh | Dữ liệu Viện Dinh dưỡng, chuẩn Bộ | Danh mục dùng chung | PHT có `NUTRITION_MANAGE`; HT |
| Thực đơn (App PH, App GV) | Thực đơn tuần đã công bố, ảnh bữa theo ngày | — | — | PH (Tr); GV (P) |

## 4. Mã quyền

| Mã | Phạm vi | Mặc định | HT cấu hình cho | Nghiệp vụ |
| --- | --- | --- | --- | --- |
| `MEAL_COUNT_CONFIRM` (đã có) | điểm trường, GRANT nâng T | HT; PHT (bật, điểm trường mình) | PHT | Xác nhận, xác nhận lại suất (NUT-02 — C) |
| `NUTRITION_MANAGE` (plan L1) | T | HT | PHT (mặc định tắt) | Danh mục, cấu hình nhóm tuổi, NCC |
| `MENU_MANAGE` | T | HT | PHT (mặc định tắt) | Bộ mẫu, kế hoạch tuần, món thay thế, **công bố** (NUT-08: công bố là duyệt) |
| `STOCK_MANAGE` | điểm trường, GRANT nâng T | HT | PHT (mặc định tắt) | Phiếu nhập kho khô, bản dự trù, kiểm nhận hàng tươi |
| `FOOD_SAFETY_APPROVE` | điểm trường, GRANT nâng T | HT | PHT (mặc định tắt) | Duyệt lưu/hủy mẫu, in biểu mẫu |
| `KITCHEN_RECORD` | TK | Bếp | Bếp | Ghi lưu/hủy mẫu, đăng ảnh bữa |
| `MENU_PUBLISHED_EDIT` (ngoại lệ) | T | HT | PHT (mặc định tắt) | Sửa thực đơn đã công bố khi bất khả kháng (NUT-08, P-39 duyệt 02/11) |

PHT không có mã sửa vẫn xem dữ liệu bán trú mọi điểm trường (AUTH-20 — C). HT có sẵn các mã để làm thay khi PHT vắng, theo cách NUT-02 đang áp cho số suất (P cho các mã ngoài `MEAL_COUNT_CONFIRM`). Khung giờ ảnh bữa: phần cố định của HT (NUT-16 — C), không qua ma trận.

## 5. Permission matrix (Function × Actor × Action × Scope × Conditions)

Cột actor ghi phạm vi được làm; "—" = không. Admin không có quyền bán trú nào (AUTH-06, P-16) nên không lặp cột. Audit: **Có** = ghi người, thời điểm, trước/sau (và lý do khi bắt buộc) qua module audit.

| Chức năng | Action | HT | PHT có mã | PHT khác | GV | Bếp | PH | Điều kiện · giới hạn | Audit | TT |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Thực phẩm, món, chất dị ứng của thực phẩm | View | T | T | T | — | — | — | | — | C (AUTH-20) |
| | Create/Update | T | T (`NUTRITION_MANAGE`) | — | — | — | — | Nguồn dinh dưỡng bắt buộc; món tham khảo chỉ đọc | Có | C (NUT-05, NUT-18) |
| | Delete | T | T | — | — | — | — | Chỉ ngừng dùng (INACTIVE), không xóa cứng | Có | P |
| | Import | T | T | — | — | — | — | Tất cả hoặc không | Có | P |
| Cấu hình nhóm tuổi (bữa, chuẩn, giá suất) | View | T | T | T | — | — | — | | — | C |
| | Create/Update | T | T (`NUTRITION_MANAGE`) | — | — | — | — | Chỉ bản có ngày hiệu lực > hôm nay | Có | C (NUT-17); khóa bản đã hiệu lực P |
| Nhà cung cấp | View | T | T | T (ẩn CCCD) | — | — | — | CCCD chỉ người có mã | — | C (NUT-12); ẩn CCCD P |
| | Create/Update/Delete | T | T (`NUTRITION_MANAGE`) | — | — | — | — | Delete = ngừng dùng | Có | C (NUT-12) |
| Bộ mẫu, kế hoạch tuần, món thay thế | View | T | T | T | — | — | — | Bản nháp chỉ BGH thấy | — | C |
| | Create/Update/Delete | T | T (`MENU_MANAGE`) | — | — | — | — | Chỉ tuần DRAFT | Có | C (NUT-08, NUT-13, NUT-23) |
| Công bố thực đơn tuần | Publish | T | T (`MENU_MANAGE`) | — | — | — | — | Mọi ngày đạt cân đối; cả trường một lần; không chờ suất; thiếu kho chỉ cảnh báo | Có | C (NUT-08, NUT-11) |
| Thực đơn tuần đã công bố | View | T | T | T | T (P) | TK | Tr | PH chỉ bản đã công bố (PAR-02) | — | C (AUTH-05, PAR-01); GV P |
| | Update (ngoại lệ) | T | T (`MENU_PUBLISHED_EDIT`) | — | — | — | — | Chỉ ngày chưa tới hoặc hôm nay trước giờ chốt; lý do bắt buộc; báo bếp + PH; ngày đã có suất xác nhận ⇒ tính lại trừ kho | Có, lý do | C (NUT-08, P-39) |
| | Export (in thực đơn tuần) | T | T | T | — | — | — | | — | P |
| Điểm danh = báo ăn | Create/Update | — | — | — | L | — | — | Trước giờ chốt hoặc khi được mở lại | Có | C (ATT-01, ATT-02) |
| | Mở lại sau giờ chốt | T | điểm trường | điểm trường | — | — | — | Lý do bắt buộc | Có, lý do | C (ATT-08) |
| Sổ suất ăn | View | T | T | T | L (P) | TK (chỉ số đã xác nhận) | — | Bếp không thấy tên trẻ | — | C (NUT-03, AUTH-05, AUTH-20) |
| | Confirm / xác nhận lại | T | điểm trường (`MEAL_COUNT_CONFIRM`) | — | — | — | — | Sau giờ chốt; số sai ⇒ mở lại điểm danh, không sửa tay | Có | C (NUT-02..04) |
| | Điều chỉnh có lý do | — | điểm trường | — | — | — | — | Endpoint `adjustments` hiện có; đối chiếu lại NUT-04 | Có, lý do | cần đối chiếu (module card) |
| Trừ kho khô theo suất | (hệ thống) | — | — | — | — | — | — | Khi suất điểm trường × ngày được xác nhận; lô hết hạn trước; thiếu ⇒ ghi thiếu, không chặn | Có (bút toán) | C (NUT-11, P-38) |
| Kho khô: tồn, lịch sử | View | T | T | T | — | — | — | | — | C (NUT-11) |
| Phiếu nhập kho khô | Create | T | điểm trường (`STOCK_MANAGE`) | — | — | — | — | NCC, lô, hạn dùng, giá; xác nhận ⇒ tạo lô, trừ tiếp phần thiếu | Có | C (NUT-11) |
| | Update/Delete | T | điểm trường | — | — | — | — | Trước xác nhận: sửa/xóa. Sau xác nhận: chỉ hủy bằng bút toán đảo khi lô chưa bị trừ, lý do bắt buộc | Có, lý do | P |
| | Chuyển kho, kiểm kê | — | — | — | — | — | — | Không có | — | C (NUT-11) |
| Bản dự trù hàng tươi | View | T | T | T | — | — | — | | — | C |
| | Create/Update | T | điểm trường (`STOCK_MANAGE`) | — | — | — | — | Sĩ số dự kiến điền sẵn, sửa được | Có | C (NUT-07, NUT-12) |
| | Export (in đặt hàng) | T | điểm trường | — | — | — | — | | — | C (NUT-12) |
| Kiểm nhận hàng tươi | Create/Update | T | điểm trường (`STOCK_MANAGE`) | — | — | — (báo PHT ngoài hệ thống) | — | Lần 2 mở khi suất ngày đã xác nhận | Có | C (NUT-12) |
| Bếp hôm nay | View | T | T | T | — | TK | — | Dị ứng chỉ dạng số suất | — | C (AUTH-05, NUT-23) |
| Lưu mẫu, hủy mẫu | Create | — | — | — | — | TK (`KITCHEN_RECORD`) | — | Hủy sau ≥24 giờ kể từ lấy mẫu | Có (theo tài khoản — AUTH-22) | C (NUT-21) |
| | Approve | T | điểm trường (`FOOD_SAFETY_APPROVE`) | — | — | — | — | Bếp sửa được khi chưa duyệt; đã duyệt thì khóa | Có | C (NUT-21); khóa sau duyệt P |
| | Export (nhãn, biểu mẫu) | T | điểm trường | — | — | — | — | Chỉ bản ghi đã duyệt | — | C (NUT-21) |
| Ảnh bữa | Create/Update | — | — | — | — | TK (`KITCHEN_RECORD`) | — | Chỉ trong khung giờ theo bữa | Có | C (NUT-16) |
| | View | T | T | T | — | TK | điểm trường của con | | — | C (NUT-16, PAR-01) |
| Khung giờ ảnh bữa | Update | T | — | — | — | — | — | Chung toàn trường | Có | C (NUT-16) |
| Bán trú hôm nay | View | T | T | T | — | — | — | Lọc theo điểm trường | — | P (màn mới) |
| Phân quyền các mã trên | Update | T | — | — | — | — | — | Lý do bắt buộc; không tự sửa mình | Có | C (AUTH-10..14) |

## 6. Ngoại lệ

| Ngoại lệ | Cách xử lý | TT |
| --- | --- | --- |
| Sửa thực đơn đã công bố | Chỉ bất khả kháng, người có `MENU_PUBLISHED_EDIT`, điều kiện ở bảng trên | C (NUT-08, P-39) |
| Điều chỉnh suất sau giờ chốt | Mở lại điểm danh (ATT-08) → GV sửa → xác nhận lại (NUT-03, NUT-04); kho ghi chênh lệch | C |
| Thay đổi dữ liệu kho | Không sửa bút toán; phiếu nhập sai ⇒ bút toán đảo có lý do; không kiểm kê, không chuyển cơ sở | C (không kiểm kê); bút toán đảo P |
| Thiếu kho khi xác nhận suất | Không chặn xác nhận; ghi phần thiếu; PHT mua thêm (B), nhập phiếu bổ sung ⇒ trừ tiếp | C (NUT-11, P-38) |
| Sai lệch hàng tươi | Ghi ở kiểm nhận (đủ/thiếu/thừa); báo NCC ngoài hệ thống | C (NUT-12) |
| Thiếu/thừa suất thực tế, thiếu nguyên liệu khi nấu | Báo PHT qua Zalo; hệ thống không ghi | C (NUT-22) |

## 7. Dữ liệu dùng chung và tách theo điểm trường

| Dùng chung toàn trường | Tách theo điểm trường | Tách theo lớp / trẻ |
| --- | --- | --- |
| Thực phẩm, món, cấu hình nhóm tuổi, NCC, bộ mẫu, kế hoạch và thực đơn tuần đã công bố, món thay thế dị ứng, khung giờ ảnh | Sổ suất, lô và bút toán kho khô, phần thiếu, bản dự trù, kiểm nhận hàng tươi, lưu/hủy mẫu, ảnh bữa, tài khoản bếp | Điểm danh, suất theo lớp; dị ứng của trẻ (module child, bếp chỉ thấy số đếm) |

Căn cứ: SCH-06, NUT-11, NUT-13, NUT-16, AUTH-05, AUTH-22.

## 8. Giảm thao tác lặp

- Một thực đơn tuần là nguồn cho: bản dự trù, nhu cầu ngày, trừ kho, bếp hôm nay, lưu mẫu (món điền sẵn), phụ huynh.
- Số suất xác nhận một lần ⇒ tự trừ kho, cập nhật bếp hôm nay và kiểm nhận lần 2; không nhập lại số ở màn nào khác.
- Sĩ số dự kiến điền sẵn từ trẻ đang học; kiểm nhận lần 1 điền sẵn từ bản dự trù.
- Sao tuần từ bộ mẫu; chép định lượng giữa nhóm tuổi; món thay thế dùng chung theo chất, không lập từng trẻ.
- Nhãn và biểu mẫu lưu mẫu sinh từ bản ghi đã duyệt.

## 9. Thay đổi so với thiết kế hiện tại

| Hiện tại | Mới | Căn cứ |
| --- | --- | --- |
| Công bố thực đơn chuẩn theo ngày sau chốt suất (rule 01/11), plan cũ có lưới trạng thái từng ngày | Công bố cả tuần một lần; đến ngày chỉ thêm ảnh | User chốt 02/11 |
| Trừ kho khô khi công bố ngày, chặn công bố nếu thiếu | Trừ khi suất điểm trường được xác nhận; thiếu không chặn | NUT-11 (P-38 duyệt 02/11) |
| FE: 4 nhóm menu (Danh mục, Dinh dưỡng, Kho, Báo cáo bếp) + 2 trang quy trình | 6 nhóm ở mục 1, một trang "Bán trú hôm nay" | Đề xuất |
| FE: bếp dùng Web | Bếp chỉ App | AUTH-21 |
| FE: xuất kho, bếp chọn lô | Trừ tự động, lịch sử nhập/trừ | NUT-11 |
| FE: hàng tươi bếp ghi, PHT duyệt | PHT ghi 2 lần | NUT-12 |
| FE: thực đơn mẫu một ngày; thực đơn thay thế nguyên ngày theo nhóm tuổi | Bộ 4 tuần có nhãn mùa; món thay thế theo chất × món | NUT-13, NUT-23 |
| FE: trạng thái chuẩn bị, bàn giao | Bỏ; bàn giao là việc ngoài hệ thống | NUT-14, NUT-15 REMOVED |
| Quyền bán trú theo role cứng (`only(VP_ONLY…)` ở FE) | Mã quyền ở mục 4, cấu hình qua ma trận | AUTH-10, AUTH-20 |
| "Sổ báo ăn" trong nhóm Điểm danh | Chuyển sang Vận hành bếp cho PHT/HT; GV vẫn điểm danh ở nhóm Điểm danh | NUT-02 |

## 10. Còn mở

P-26 (mẫu sổ lưu mẫu), P-27 (kiểm thực ba bước), P-16 (Admin xem dữ liệu trẻ). GV xem thực đơn và sổ suất lớp mình (đánh dấu P ở bảng), khóa bản lưu mẫu sau duyệt, bút toán đảo phiếu nhập: chờ user xác nhận.
