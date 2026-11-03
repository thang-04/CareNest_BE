---
title: Tự tạo tài khoản phụ huynh + in phiếu theo lớp (phần tài khoản)
status: draft
owner: thangnd
jira: chưa có — hỏi user (Câu hỏi mở 4)
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, child]
rules: [AUTH-08, AUTH-18, AUTH-19]
risk: high
created: 2026-10-18
updated: 2026-10-18
---
# Tự tạo tài khoản phụ huynh + in phiếu theo lớp (phần tài khoản)

> **Bị thay một phần (2026-10-28):** user chốt gửi mật khẩu qua SMS thay in phiếu, kích hoạt khi lưu form tiếp nhận hoặc ở màn Tài khoản phụ huynh — `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md`. "AUTH-20", "AUTH-21" trong plan này là đề xuất cũ, **khác nghĩa** AUTH-20, AUTH-21 hiện có ở `docs/business/BUSINESS_RULES.md`. Đối chiếu plan mới trước khi làm tiếp.

## Context
- Hôm nay phụ huynh có tài khoản chỉ khi người dùng nhập tay từng người: `POST /children/{id}/guardians` sinh mật khẩu tạm và trả ngay (`GuardianAccountServiceImpl.java:86-121`).
- Không đếm được theo lớp ai đã nhận phiếu, ai đã đăng nhập.
- **Tiếp nhận trẻ do người khác làm** (user, 2026-10-18), ngoài plan này.
  - BE hiện chưa có `POST /children` (`ChildController.java:33-56` chỉ có GET); `children` chỉ có họ tên và ngày sinh (`V3__create_children.sql:2-8`).
  - Plan này chỉ cung cấp **điểm nối** để luồng tiếp nhận gọi sau khi tạo trẻ, không viết API tiếp nhận.
- Màn P1 đã dựng trên API hiện có (plan `2026-10-17-parent-account-provisioning.md`, log 2026-10-18). Ba số "đã in", "đã đăng nhập" và ca lỗi đang chờ plan này.

## Quyết định đã chốt
1. Có học sinh thì phải có dữ liệu phụ huynh đi kèm. Hệ thống tự tạo tài khoản khi tiếp nhận trẻ — user, 2026-10-18.
2. Có cả hai cách: tự tạo khi tiếp nhận, và tạo hàng loạt theo lớp cho trẻ đã có — user, 2026-10-18.
3. Mật khẩu tạm cấp lúc in phiếu. In lại = mật khẩu mới, phiếu cũ hết hiệu lực — user, 2026-10-18.
4. SĐT phụ huynh bắt buộc. Ca lỗi không chặn tiếp nhận: bỏ qua và đưa vào "Cần xử lý" — user, 2026-10-18.
5. Đếm theo lớp: có tài khoản / đã in phiếu / đã đăng nhập lần đầu — user, 2026-10-18.
6. Màn phụ huynh theo wireframe P1 — user, 2026-10-18.
7. Tiếp nhận trẻ (API, form, xếp lớp) thuộc người khác; plan này chỉ làm phần tài khoản và phân quyền — user, 2026-10-18.

## Làm rõ nghiệp vụ
- **Actor và outcome:**
  - Luồng tiếp nhận (của người khác) gọi `ParentProvisioningService.provisionForChild(childId, guardians)`.
  - Hệ thống tạo tài khoản Keycloak **chưa có mật khẩu** (chưa đăng nhập được) và liên kết trẻ; ca lỗi được ghi lại.
  - Người có `PARENT_ACCOUNT_MANAGE` (HT, PHT campus mình — AUTH-18) xử lý ca lỗi và in phiếu theo lớp. Hệ thống cấp mật khẩu tạm và ghi giờ in.
  - Request đầu tiên vào BE sau khi phụ huynh đổi mật khẩu ghi giờ đăng nhập lần đầu.
- **Rule:**
  - AUTH-18 sửa: tài khoản tạo không kèm mật khẩu; mật khẩu cấp khi in.
  - Thêm AUTH-20: tự tạo từ dữ liệu phụ huynh của trẻ; SĐT bắt buộc; ca lỗi vào "Cần xử lý".
  - Thêm AUTH-21: in phiếu cấp mật khẩu; theo dõi đã in / đã đăng nhập.
  - Ghi vào `BUSINESS_RULES.md` ở phase 1, sau khi plan duyệt.
- **Lệch thiết kế ban đầu:** AUTH-18 (`BUSINESS_RULES.md:30`) ghi "mật khẩu tạm chỉ hiện một lần để in phiếu" lúc tạo. Nay tạo và in tách thành hai bước.
- **Ảnh hưởng:**
  - Module: identity-access (tạo/in/trạng thái tài khoản, ghi lần đăng nhập đầu ở `security/CurrentUserService.java:48-70`); child (liên hệ phụ huynh, liên kết).
  - FE: `/children/activation`.
  - APP: không đổi; lần đầu vẫn đổi mật khẩu qua Keycloak `UPDATE_PASSWORD`.
  - Người làm tiếp nhận: gọi service ở điểm nối, không tự tạo tài khoản.
  - Dữ liệu cũ: phụ huynh đã tạo tay ⇒ coi như đã in (giờ in = giờ tạo); giờ đăng nhập đầu tự điền ở lần dùng app kế tiếp.

### Câu hỏi mở
- [ ] 1. Phối hợp với người làm tiếp nhận: SĐT, họ tên, quan hệ phụ huynh lưu ở đâu? Đề xuất: bảng `child_guardian_contacts` thuộc plan này; luồng tiếp nhận chỉ truyền danh sách phụ huynh vào `provisionForChild`. Nếu họ đã có bảng riêng thì plan đọc từ bảng đó, không tạo bảng mới. Cần bạn hỏi lại người đó.
- [ ] 2. "Tạo hàng loạt theo lớp" lấy SĐT từ đâu? Đề xuất: từ liên hệ phụ huynh đã lưu mà chưa có tài khoản hoặc đang lỗi (tạo lại). Không nhập file Excel ở V1.
- [ ] 3. Định nghĩa trạng thái:
  - "đã in" = đã cấp mật khẩu tạm ít nhất một lần;
  - "đã đăng nhập" = lần đầu dùng app thành công (sau khi đổi mật khẩu);
  - "In phiếu cả lớp" mặc định chỉ in cho người **chưa in**. In lại người đã đăng nhập phải chọn từng người, vì làm họ mất phiên.

  Đồng ý?
- [ ] 4. Mã Jira cho plan (khoảng 15 file, 3 phase); làm tiếp trên nhánh `G94-60` (auth) được không?

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |

## Acceptance criteria
- **AC-1** (AUTH-20): `provisionForChild` với SĐT mới ⇒ tạo tài khoản PARENT trong Keycloak **không mật khẩu** + liên kết; SĐT đã là phụ huynh ⇒ chỉ liên kết, giữ mật khẩu.
- **AC-2** (AUTH-20): SĐT sai định dạng, SĐT của nhân sự (từ 2026-10-28 chỉ còn khi SĐT là tên đăng nhập tài khoản nhân sự cũ — AUTH-18), hoặc Keycloak lỗi ⇒ không ném lỗi ra luồng gọi; liên hệ ghi `issue` (`INVALID_PHONE` | `PHONE_OF_STAFF` | `IDP_UNAVAILABLE`); không để Keycloak có user mồ côi.
- **AC-3** (AUTH-20, AUTH-08): Sửa liên hệ đang lỗi ⇒ tạo lại ngay. `POST /classrooms/{id}/parent-accounts` ⇒ xử lý lại mọi liên hệ chưa có tài khoản của lớp, trả số tạo / liên kết / còn lỗi. PHT ngoài campus ⇒ 403.
- **AC-4** (AUTH-21): `POST /classrooms/{id}/parent-slips` không truyền danh sách ⇒ chỉ cấp mật khẩu cho phụ huynh chưa in. Ghi `password_issued_at`. Trả mật khẩu một lần, không ghi log, `Cache-Control: no-store`. Cấp lại một người cũng ghi giờ in.
- **AC-5** (AUTH-21): Request hợp lệ đầu tiên của tài khoản có `first_login_at` rỗng ⇒ ghi giờ. Các request sau không ghi nữa.
- **AC-6** (AUTH-21, AUTH-08): `GET /parent-accounts/class-coverage?campusId=` trả mỗi lớp: sĩ số, số bé có ≥1 phụ huynh có tài khoản / đã in / đã đăng nhập, số bé có lỗi. Chỉ trong phạm vi người xem.
- **AC-7** (AUTH-18, AUTH-19): API phụ huynh hiện có giữ hành vi. Thêm tay một phụ huynh vẫn trả mật khẩu ngay (tính là đã in) và ghi liên hệ. Gỡ phụ huynh khỏi bé xóa cả liên hệ.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Điểm nối với tiếp nhận | Service Java `ParentProvisioningService.provisionForChild` gọi trong cùng ứng dụng | Endpoint REST riêng để FE gọi sau khi tiếp nhận | Tiếp nhận và tạo tài khoản không lệch nhau; FE không phải gọi 2 lần |
| Nơi lưu SĐT phụ huynh | Bảng `child_guardian_contacts` (child, họ tên, quan hệ, SĐT, `user_id` null được, `issue`) — chờ câu 1 | Chỉ dùng `guardian_child_links` | Liên kết cần có tài khoản; ca lỗi vẫn phải giữ SĐT để sửa và tạo lại |
| Gọi Keycloak | Đồng bộ; lỗi ⇒ `issue`, không rollback trẻ | Hàng đợi / job nền | Không thêm hạ tầng; tạo lại bằng tay hoặc theo lớp |
| Trạng thái in / đăng nhập | Hai cột null được trên `user_accounts`: `password_issued_at`, `first_login_at` | Bảng riêng cho phụ huynh | Dùng chung cho nhân sự, không cần join |
| Ghi lần đăng nhập đầu | Ở `CurrentUserService` khi cột đang rỗng: một câu UPDATE có `WHERE first_login_at IS NULL` | Event từ Keycloak | Chạy cho cả web và APP, không cần cấu hình Keycloak |

## Phases
### Phase 1 — Rule, schema, tạo tài khoản từ liên hệ
- Files:
  - C `db/migration/V10__guardian_contacts_and_account_milestones.sql` (bảng liên hệ + CHECK SĐT/quan hệ/issue; 2 cột `user_accounts`; backfill liên hệ từ `guardian_child_links`; `password_issued_at = created_at` cho PARENT).
  - C entity/repository `ChildGuardianContact`; C `service/account/ParentProvisioningService(+Impl)` (dùng `AccountLifecycleOperations.createIdentity` với `temporaryPassword = null`, client đã hỗ trợ ở `KeycloakIdentityProviderClient.java:70-74`).
  - M `UserAccount`, `GuardianAccountServiceImpl` (thêm tay ghi liên hệ và giờ in), `GuardianController` (sửa liên hệ, tạo theo lớp).
  - M docs: `BUSINESS_RULES`, `DATA_DICTIONARY`, `ERD`, `child.md`, `identity-access.md` (mục điểm nối cho tiếp nhận), `AUTH_CONTRACT.md`, `openapi.yaml`.
- Tests: AC-1..AC-3, AC-7 (`ParentProvisioningServiceImplTest` với Keycloak giả, Testcontainers cho CHECK/backfill, WebMvc 403).
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0. **Dừng chờ review.**

### Phase 2 — In phiếu theo lớp, đếm, đăng nhập lần đầu
- Files:
  - C `POST /classrooms/{id}/parent-slips`, `GET /parent-accounts/class-coverage`.
  - M `GET /children` (thêm `parentAccountState` cho mỗi bé), `GuardianResponse` (thêm `contactId`, `phone`, `issue`, `passwordIssuedAt`, `firstLoginAt`; `userId` null được), `POST /parent-accounts/{id}/temporary-password` (ghi giờ in), `CurrentUserService` (AC-5).
- Tests: AC-4..AC-6. Kiểm log: mật khẩu không xuất hiện trong log.
- Exit: như phase 1 + snapshot OpenAPI cập nhật. **Dừng chờ review.**

### Phase 3 — FE (`CareNest_FE`, khi user cho phép trong lượt đó)
- `/children/activation`:
  - 3 số theo lớp lấy từ coverage;
  - chip "Cần xử lý / Chưa in / Chưa đăng nhập";
  - "Tạo tài khoản cho cả lớp";
  - "In phiếu cả lớp" (modal xác nhận + nhiều phiếu);
  - panel bé hiện ca lỗi (sửa SĐT, tạo lại), giờ in, lần đăng nhập đầu.
- Mock chép luật; docs FE `ROUTE_MAP`, `BACKEND_INTEGRATION`.
- Exit: FE `node scripts/verify.mjs` → `VERIFY PASS full`; chụp màn mock 1280/375. **Dừng chờ review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2 | AUTH-20 | `ParentProvisioningServiceImplTest` (unit, Keycloak giả) | 1 | — |
| AC-3 | AUTH-20, AUTH-08 | `ParentProvisioningServiceImplTest`, `GuardianControllerTest` (WebMvc) | 1 | — |
| AC-7 | AUTH-18, AUTH-19 | `GuardianAccountServiceImplTest` (giữ test cũ) | 1 | — |
| AC-4, AC-6 | AUTH-21, AUTH-08 | `ParentSlipServiceImplTest`, `ClassCoverageQueryTest` (Testcontainers) | 2 | — |
| AC-5 | AUTH-21 | `CurrentUserServiceTest` | 2 | — |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Trùng số migration V10 với nhánh G94-200 (đang giữ V7–V9 cần đánh số lại) hoặc nhánh tiếp nhận | Flyway lỗi khi gộp | Báo người làm tiếp nhận số V10; nhánh gộp sau đổi số | Đổi tên file trước khi merge |
| Người làm tiếp nhận không gọi điểm nối | Trẻ mới không có tài khoản | Mục điểm nối trong `identity-access.md`; "Tạo theo lớp" vẫn bù được | — |
| In phiếu cả lớp làm phụ huynh đã đăng nhập mất phiên | Phụ huynh bị đăng xuất | Mặc định chỉ người chưa in; chọn tay mới in lại (câu 3) | — |
| Mật khẩu hàng loạt lộ qua log hoặc cache | Lộ tài khoản | `toString` che mật khẩu, `no-store`, test log | Thu hồi bằng cấp lại |
| UPDATE `first_login_at` ở mọi request | Thêm truy vấn | Chỉ chạy khi cột rỗng (một lần mỗi tài khoản) | Bỏ dòng gọi |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: FE `GuardianPanel.jsx`, `ParentActivationPage.jsx`; luồng tiếp nhận (người khác) gọi `provisionForChild`; APP không dùng các endpoint này
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] ~15 file, 3 phase ⇒ vừa một plan (đã bỏ phần tiếp nhận)
- [x] Không dùng rule PENDING/OPEN như đã chốt (AUTH-20/21 chỉ ghi sau khi duyệt)
- [x] Tự review: không placeholder · không mâu thuẫn · không câu mơ hồ

## Progress log
### 2026-10-18 — Viết plan (draft)
- User: "viết plan luồng tài khoản phụ huynh tự động đi". Bản đầu có phần tiếp nhận trẻ.
- User: "phần tiếp nhận trẻ đã có người khác làm, tập trung làm các phần về phân quyền". Bỏ API tiếp nhận, quyền `CHILD_ENROLL` và form; giữ phần tài khoản + điểm nối `provisionForChild`. Chờ trả lời 4 câu hỏi mở và duyệt.
