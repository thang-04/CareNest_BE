---
title: Ảnh trẻ và phụ huynh khi tiếp nhận
status: in-progress
owner: thang-04
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [child, identity-access, attendance]
rules: [CHD-01, CHD-02, AUTH-04, PCK-02]
risk: high
created: 2026-11-05
updated: 2026-11-05
---
# Ảnh trẻ và phụ huynh khi tiếp nhận

## Context
User yêu cầu 05/11/2026 code nút tải ảnh trẻ và phụ huynh trên FE/BE. Cho phép sửa cả hai repo; không có quyền commit/push. Mã Jira ở frontmatter theo nhánh hiện tại, chưa có yêu cầu tạo task/commit mới.
Hiện trạng: `ChildGuardian.java:29` lưu phụ huynh trước khi có tài khoản nhưng chưa có ảnh; `UserAccount.java:57` có ảnh tài khoản. `ParentPhotoServiceImpl.java:46` chỉ phụ huynh tự tải ảnh. `AccountPhotoController.java:34` giới hạn upload role PARENT. `PickupServiceImpl.java:119` chỉ lấy ảnh tài khoản liên kết. `ChildViewResponse.java:16` chưa trả ảnh trẻ.

## Quyết định đã chốt
1. Có nút tải ảnh trẻ và từng phụ huynh ở tiếp nhận, làm FE và BE — user 05/11/2026.
2. Giữ stack và lưu trữ private đang có; không thêm dependency, APP, camera, nhận diện khuôn mặt hoặc link upload công khai vào phạm vi.

## Làm rõ nghiệp vụ
Actor: nhân sự có CHILD_ENROLL đúng campus; outcome: ảnh hồ sơ để hiển thị avatar và đối chiếu khi trả trẻ.
Rules hiện có CHD-01/02, AUTH-04, PCK-02 (CONFIRMED). FR/AC và các quyết định chưa duyệt ở [Domain spec](../../../specs/012-child-enrollment-photos/domain-spec.md).
Flow: [B] lấy ảnh gia đình → [S] chọn/xem trước → [B] xác nhận → [S] kiểm quyền, ảnh, lưu → [S] đọc ảnh qua API riêng tư.
PCK-02 hiện yêu cầu ảnh APP lần đầu; đề xuất thêm ảnh do nhà trường tải, không bỏ yêu cầu APP. Ảnh tùy chọn lúc tiếp nhận là đề xuất mới, chờ user duyệt.
Ảnh hưởng cụ thể và request/response tại [draft contract](../../../specs/012-child-enrollment-photos/contracts/enrollment-photos.md). Dữ liệu cũ không có ảnh tiếp tục dùng initials/fallback.

### Câu hỏi mở
- [x] User duyệt gói đề xuất: ảnh không bắt buộc; ảnh phụ huynh theo hồ sơ trẻ, ưu tiên khi đón, fallback ảnh APP; JPEG/PNG/WEBP ≤2 MiB/ảnh; triển khai một phase BE+FE rồi review.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Có sửa FE và BE? | Có, user yêu cầu rõ | user | 2026-11-05 | Domain spec mục 2 |
| Ảnh có bắt buộc, có thay ảnh tài khoản? | Đề xuất không bắt buộc, lưu theo hồ sơ; chờ duyệt | chưa có xác nhận | 2026-11-05 | Domain spec mục 14 |

## Acceptance criteria
AC-1..AC-8 của Domain spec là tiêu chí nghiệm thu đề xuất: preview đúng người; lưu/đọc/avatar; JSON cũ; kiểm tệp trước side effect; scope; pickup chưa kích hoạt/fallback; lỗi lưu/rollback; sửa hồ sơ giữ ảnh đúng người.

## Key decisions
| Quyết định | Đề xuất | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nơi lưu ảnh phụ huynh | ChildGuardian, fallback UserAccount | Ghi đè ảnh tài khoản | Lưu được khi chưa kích hoạt, tránh thay ảnh của mọi trẻ liên kết |
| Gửi ảnh | Multipart ở cùng endpoint tiếp nhận, giữ JSON cũ | Lưu trẻ rồi upload nhiều request | Kiểm ảnh trước side effect, tránh báo lưu thất bại sau khi trẻ đã được tạo |
| Bắt buộc ảnh | Không | Bắt buộc mọi phụ huynh | Một buổi tiếp nhận có thể thiếu người; cần user duyệt |
| Định dạng/dung lượng | JPEG/PNG/WEBP ≤2 MiB | Nhận mọi image/* | Theo convention ảnh tài khoản, kiểm nội dung ở BE |

## Phases
### Phase 1 — BE và FE trong một lượt triển khai, review sau kiểm chứng
Trước code hoàn tất feature-spec-loop: spec, clarify, plan kỹ thuật, data-model, tasks, analyze. `.specify/memory/constitution.md` hiện không có; đối chiếu nguyên tắc AGENTS và hoàn thiện artifact theo workflow trước implement, không tự đổi stack.
Do >8 file và ba module, chia task BE/FE theo dependency trong cùng phase; giữ một điểm review cuối để tránh dừng giữa một chức năng chưa dùng được.
- BE: migration mới; Child/ChildGuardian; DTO child; controller multipart; orchestration enrollment; service/store ảnh; guardian photo contacts; bảo toàn ảnh khi sửa hồ sơ; pickup đọc nguồn ảnh mới; OpenAPI snapshot.
- FE: chọn ảnh ở EnrollChildPage/ChildInfoFields, preview bước xem lại, adapter multipart và mock, ảnh có token ở hồ sơ/avatar/pickup, xử lý lỗi đúng ô và thu hồi object URL.
- Tests: AC-1..8 theo Test matrix. Không sửa phần cân đo đang có dirty work.
- Exit: cả hai repo `node scripts/verify.mjs` → VERIFY PASS full, skip 0; `node scripts/check-ai-layer.mjs` → exit 0; browser mock và BE thật nếu cấu hình sẵn, ghi rõ phần chưa chạy.
- **Dừng chờ user review sau phase.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | CHD-01/02 | FE preview, chuyển bước, thay/bỏ ảnh từng người | 1 | Đạt; bằng chứng ở Spec012 quickstart |
| AC-2/3 | CHD-02 | BE multipart/read + JSON không ảnh; FE save/mở lại/avatar | 1 | Đạt; bằng chứng ở Spec012 quickstart |
| AC-4 | CHD-01 | BE MIME giả, rỗng, >2 MiB, part sai/lặp; không side effect | 1 | Đạt; bằng chứng ở Spec012 quickstart |
| AC-5 | AUTH-04 | BE upload/read ngoài campus/class/child | 1 | Đạt; bằng chứng ở Spec012 quickstart |
| AC-6 | PCK-02 | BE pickup ảnh hồ sơ, chưa kích hoạt, fallback; FE blob có token | 1 | Đạt; bằng chứng ở Spec012 quickstart |
| AC-7 | CHD-01 | BE rollback/storage lỗi và cleanup; FE giữ form/retry | 1 | Đạt; bằng chứng ở Spec012 quickstart |
| AC-8 | CHD-02 | BE sửa hồ sơ/đổi thứ tự/đổi phụ huynh giữ đúng ảnh | 1 | Đạt; bằng chứng ở Spec012 quickstart |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Ảnh lộ hoặc gắn sai người | Sai đối chiếu đón | Scope server, id ổn định, regression reorder | Ngừng đường multipart mới, giữ JSON và metadata |
| S3/DB/Keycloak lỗi | Tệp mồ côi, hồ sơ không rõ trạng thái | Kiểm ảnh trước side effect, cleanup rollback, giữ compensation hiện có | Revert source có chọn lọc; không sửa/xóa migration đã áp dụng |
| Sửa hồ sơ thay ChildGuardian | Mất hoặc chuyển ảnh | Match danh tính, giữ id/metadata đúng người | Giữ dữ liệu ảnh; sửa forward bằng migration/code mới |
| Môi trường thiếu S3/credential | Chưa chứng minh upload thật | Test storage mock, ghi rõ giới hạn, không tạo cloud | Không thay cấu hình/secret có sẵn |

## Validation log
- [x] Claims có đường dẫn source và dòng; kiểm trực tiếp ngày 05/11.
- [x] Consumer cụ thể được liệt kê trong draft contract.
- [x] Mỗi AC có test, exit commands; chưa gán kết quả khi chưa implement.
- [x] >8 file đã tách task BE/FE cùng một phase review cuối.
- [x] Quyết định mới là đề xuất; không tự nâng thành CONFIRMED.
- [x] Tự review: không đổi route userId thành guardianId, không lưu theo index sau tạo, không hứa atomic S3/Keycloak/SMS.

## Progress log
### 2026-11-05 — Draft chờ duyệt
- Đọc source tiếp nhận/ảnh tài khoản/pickup, chuẩn bị Domain spec và draft contract. Chưa sửa source, migration, dependency hoặc API.
- Chưa có phê duyệt plan; chưa chạy test ứng dụng cho tính năng. Check-ai-layer: 1 lỗi/12 cảnh báo; lỗi ở link FE của incident `docs/knowledge/incidents/BUG-261102-curriculum-reimport-duplicate-headings.md:36`, đã có trong HEAD và nằm ngoài thay đổi này. Không sửa tài liệu ngoài phạm vi để làm xanh cổng. Diff whitespace đạt.
- Tiếp: user duyệt gói đề xuất; hoàn thiện Spec Kit artifacts/analyze và triển khai phase 1.

### 2026-11-05 — User duyệt, bắt đầu phase 1
- User trả lời “duyệt”, chấp thuận gói phương án đã trình và triển khai FE/BE. Không có quyền commit/push. Các entry trước giữ để truy vết lịch sử.

### 2026-11-05 — Phase 1 triển khai xong, chờ review
- BE: VERIFY PASS full | tests 1073 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 516s | log target/verify.log. FE: VERIFY PASS full, 799 tests, zero fail/skip.
- Actual Chromium mock 1440/375 chọn/bỏ/chọn lại, xóa phụ huynh, chuyển bước, lưu/mở lại/reload có ảnh; 0 lỗi JS, không tràn ngang; 9 URL tạo/9 thu hồi mỗi viewport. Ảnh fixture tổng hợp, không dữ liệu thật.
- Review độc lập tìm bốn vấn đề; đã sửa và thêm regression, review lại không còn blocker. Converge cuối sạch trong phạm vi ảnh đã duyệt; tasks T001..T012 hoàn tất.
- Khác plan theo source: bổ sung GET /children/{id}/profile vì FE đã gọi nhưng BE chưa có; response chỉ child view hiện có, không giả lập khai báo/lịch sử lớp. Scope đọc dùng CampusAccessPolicy AUTH-20, tách metadata quản trị tài khoản theo quyền và campus. BE chưa có PUT sửa hồ sơ, không mở thêm feature sửa hồ sơ.
- Giới hạn: chưa chạy upload S3 thật hoặc browser-to-live-BE; module children/pickup FE vẫn giữ nguồn demo hiện có. API adapters và BE kiểm độc lập. AI-layer FE 0 lỗi/20 cảnh báo; BE còn 1 link incident hỏng có sẵn/12 cảnh báo, đã xác nhận tồn tại ở HEAD.
- Không commit/push. Giữ status in-progress để chờ user review phase; không còn câu hỏi nghiệp vụ mở.

### 2026-11-05 — User yêu cầu commit và push
- User cho phép commit và đẩy code lên nhánh hiện tại của cả FE/BE; dùng mã G94-180 theo nhánh, không tạo PR hoặc đổi nhánh. Không đưa artifact trình duyệt/tệp báo cáo cục bộ vào commit.
- Cổng tài liệu BE trước commit: sửa link incident FE đã có ở commit994002c sang URL blob cố định, đã xác minh git object; không đổi nội dung incident hay mã giáo dục. Check-ai-layer sau sửa: BE 0 lỗi/12 cảnh báo, FE 0 lỗi/20 cảnh báo.
