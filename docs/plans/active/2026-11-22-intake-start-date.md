---
title: Tiếp nhận chọn năm bắt buộc, ngày bắt đầu đi học năm đang học, nhắc trẻ tới ngày chưa có lớp (đợt 2)
status: draft
owner: ATPX48-69
jira: G94-180 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [child, school-structure]
rules: [G94-180/CHD-11, G94-180/CHD-13, G94-180/CHD-14, CFG-YR-01, CFG-YR-05]
risk: high
created: 2026-11-22
updated: 2026-11-22
---
# Tiếp nhận chọn năm bắt buộc, ngày bắt đầu đi học năm đang học, nhắc trẻ tới ngày chưa có lớp (đợt 2)

Đợt 2, làm sau đợt 1 (`2026-11-22-placement-age-group-history-rename.md`). Plan FE: `FE:docs/plans/active/2026-11-22-intake-start-date.md`. Có migration và đổi contract ⇒ risk high.

## Context
- Tiếp nhận năm đang học không có ngày bắt đầu: lớp tính từ hôm nay (`ChildIntakeServiceImpl.java:414`, `PlacementRules.java:89-92`). `startDate` chỉ dùng cho năm sau (`EnrollChildRequest.java:15`, `ChildIntakeServiceImpl.java:656-660`, `:698-720`). Mở lại hồ sơ dùng hôm nay (`:272`).
- `children.enrolled_on` là ngày tiếp nhận: không sửa được (`Child.java:54-55`), FE hiện "Ngày tiếp nhận" (`FE:src/pages/children/ChildProfilePage.jsx:157`), dùng kiểm ngày nghỉ học (`ChildRecordServiceImpl.java:534-537`). Vì vậy không dùng cột này cho ngày bắt đầu đi học.
- Xếp lớp chỉ thấy lớp đang hiệu lực hôm nay (`ChildRecordServiceImpl.java:337-339`). Chuyển lớp đóng lớp cũ ở hôm qua (`:392`), cho nghỉ đóng ở trước ngày nghỉ (`:566`). Lớp bắt đầu ở tương lai sẽ vi phạm `ck_enrollments_valid_range` (`V3__create_children.sql:21`). Lỗi này đã có thể xảy ra sau kích hoạt năm của Cần (HS26312 vào lớp từ 01/10/2027).
- Điểm danh, suất ăn tìm trẻ theo ngày (`EnrollmentRepository.java:14-24`), nên lớp bắt đầu ở tương lai không cần sửa code Cần.
- Tới ngày bắt đầu mà chưa có lớp thì không có gì nhắc. FE chỉ có dòng cho PHT (`FE:src/pages/children/ChildrenListPage.jsx:202-212`).

## Quyết định đã chốt
1. Ô năm học tiếp nhận bắt buộc, không chọn sẵn. Chỉ có năm ACTIVE và năm PLANNED đã tới ngày chuẩn bị; không có năm đã qua. Áp dụng cho form, mở lại hồ sơ, Excel — user 22/11.
2. Năm đang học: ngày bắt đầu đi học bắt buộc, từ hôm nay tới ngày kết thúc năm; FE mặc định hôm nay (1A) — user 22/11.
3. Ngày bắt đầu rơi vào thứ 7, chủ nhật, ngày nghỉ: vẫn lưu, FE cảnh báo; BE không chặn (2A) — user 22/11.
4. Xếp lớp lần đầu cho trẻ chờ xếp lớp: lớp tính từ max(hôm nay, ngày bắt đầu) (3A) — user 22/11.
5. Trang tiếp nhận không phụ thuộc năm trên header (4A, phần FE) — user 22/11.
6. Tới ngày bắt đầu mà chưa có lớp: BE đánh dấu `startDue`; FE gắn nhãn, đưa lên đầu, hiện dòng nhắc cho HT và PHT (A) — user 22/11.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT, PHT · tiếp nhận ghi đúng ngày trẻ bắt đầu đi học · rule mới G94-180/CHD-14 (ghi khi duyệt plan, theo 6 quyết định trên); giữ G94-180/CHD-11 (năm sau), CFG-YR-01 (năm đã đóng chỉ đọc), CFG-YR-05 (ngày chuẩn bị).
- **Đối chiếu thiết kế ban đầu:** trước đây năm đang học vào lớp ngay hôm nay; nay theo ngày bắt đầu. Năm sau giữ nguyên.
- **Ảnh hưởng:** FE: `/children/new` (tiếp nhận, mở lại hồ sơ), hộp Nhập Excel, `/children`, `/children/placement`, `/children/:id` · APP: không gọi các endpoint này · dữ liệu cũ: `start_date` null, không gắn nhãn.

### Câu hỏi mở
| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Năm tiếp nhận theo header? | Chọn bắt buộc, không năm đã qua; trang không theo header | user | 2026-11-22 | G94-180/CHD-14 |
| Ngày bắt đầu năm đang học | Từ hôm nay tới cuối năm, mặc định hôm nay | user | 2026-11-22 | G94-180/CHD-14 |
| Ngày bắt đầu vào ngày nghỉ | Lưu, cảnh báo | user | 2026-11-22 | G94-180/CHD-14 |
| Xếp lớp sau cho trẻ chờ | Lớp từ max(hôm nay, ngày bắt đầu) | user | 2026-11-22 | G94-180/CHD-14 |
| Tới ngày A chưa có lớp | Nhãn, đưa lên đầu, dòng nhắc HT/PHT | user | 2026-11-22 | G94-180/CHD-14 |

## Acceptance criteria
- **AC-1** (`G94-180/CHD-14`): Given năm 2026-2027 ACTIVE, When tiếp nhận thiếu `startDate`, hoặc ngày trước hôm nay, hoặc sau ngày kết thúc năm, Then lỗi 400 ở ô `startDate`.
- **AC-2** (`G94-180/CHD-14`): Given tiếp nhận có lớp với `startDate` tương lai, Then enrollment `valid_from = startDate`; trẻ không có trong lớp trước ngày đó, có từ ngày đó.
- **AC-3** (`G94-180/CHD-14`): Given trẻ chờ xếp lớp có `startDate` tương lai, When xếp lớp, Then lớp từ `startDate`; `startDate` đã qua ⇒ lớp từ hôm nay.
- **AC-4** (`G94-180/CHD-14`): Given trẻ có lớp chưa bắt đầu, When đổi lớp, Then thay lớp đó: không tạo 2 lớp chồng nhau, không bắt lý do, vẫn ghi lịch sử xếp lớp.
- **AC-5** (`G94-180/CHD-14`): Given trẻ có lớp chưa bắt đầu, When cho nghỉ học, Then xóa lớp chưa bắt đầu, trẻ LEFT, không lỗi ràng buộc DB.
- **AC-6** (`G94-180/CHD-14`): Given trẻ chờ xếp lớp có `startDate` ≤ hôm nay, When đọc `GET /children`, `GET /children/{id}`, Then `startDue = true`; `startDate` tương lai hoặc null ⇒ false.
- **AC-7** (`G94-180/CHD-14`): Mở lại hồ sơ và nhập Excel vào năm đang học kiểm `startDate` như AC-1 (Excel: một ngày cho cả file).
- **AC-8** (`CFG-YR-01`, `G94-180/CHD-11`): năm đã đóng ⇒ từ chối; năm sau chưa tới ngày chuẩn bị ⇒ 2412 (như hiện nay).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lưu ngày bắt đầu của trẻ | Cột mới `children.start_date` (nullable, migration mới) | Dùng `enrolled_on` · dùng `planned_admissions` cho năm đang học | `enrolled_on` là ngày tiếp nhận; `planned_admissions` làm xếp lớp lỗi 2414 (`ChildRecordServiceImpl.java:329-336`) và đụng code Cần |
| Đổi lớp trước ngày bắt đầu | Sửa lớp của enrollment chưa bắt đầu | Coi là chuyển lớp, bắt lý do | Trẻ chưa học ngày nào |
| Cho nghỉ trước ngày bắt đầu | Xóa enrollment chưa bắt đầu | `endOn` | Vi phạm `ck_enrollments_valid_range` |
| `startDue` | BE tính theo `Clock` | FE tự so ngày | Luật ở BE |

Đổi contract: `startDate` bắt buộc cho năm đang học ở `POST /children/enrollments`, `POST /children/enrollments/import`, `POST /children/enrollments/import/preview`, `POST /children/{id}/readmit`. Thêm `startDate`, `startDue` vào item `GET /children` và vào `GET /children/{id}`. Consumer: `FE:src/services/children/api/childrenApi.js` (`docs/system/FE_API_CALLS.md:92,284-286,298`) và mock FE. Cập nhật `docs/api/openapi.yaml` bằng `-Dopenapi.snapshot.update=true` (`OpenApiSnapshotTest.java:22-23`).

## Phases
### Phase 1 — Migration, ngày bắt đầu khi tiếp nhận, mở lại, Excel (AC-1, AC-2, AC-7, AC-8)
- Files: C `db/migration/V170__child_start_date.sql` (số version kiểm lại với `origin/dev` trước khi tạo) · M `entity/child/Child.java` · M `EnrollmentFormValidator.java`, `ChildIntakeServiceImpl.java`, `ChildEnrollmentImportServiceImpl.java` · M DTO response · M `docs/api/openapi.yaml` · M test `ChildEnrollmentIntegrationTest`, import test
- Exit: `./mvnw verify` ⇒ không failure mới (ngoài 5 failure Edu có sẵn), snapshot OpenAPI khớp.
- **Dừng chờ user review.**

### Phase 2 — Xếp lớp, đổi lớp, cho nghỉ với lớp chưa bắt đầu; `startDue` (AC-3 … AC-6)
- Files: M `ChildRecordServiceImpl.java` (place, withdraw) · M `PlacementRules.java` · M `ChildListServiceImpl.java` · M test integration · M `docs/business/BUSINESS_RULES.md` (G94-180/CHD-14), `docs/modules/child.md`
- Exit: như phase 1.
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-7, AC-8 | G94-180/CHD-14, CHD-11 | integration | 1 | |
| AC-2 | G94-180/CHD-14 | integration (thêm kiểm danh sách điểm danh theo ngày) | 1 | |
| AC-3 … AC-6 | G94-180/CHD-14 | integration | 2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Trùng số migration với `dev` | Flyway lỗi khi gộp | Kiểm `origin/dev` trước khi tạo; migration chưa commit mới đổi số | Đổi số trước khi commit |
| Màn khác cho rằng trẻ ACTIVE luôn có lớp hôm nay | Hiện "chưa có lớp" trước ngày bắt đầu | Hồ sơ hiện lớp kèm "từ dd/mm" (FE) | — |
| FE cũ gửi thiếu `startDate` | 400 ở năm đang học | Gộp BE và FE cùng lúc | Revert cả hai |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (`childrenApi.js`, mock FE; APP không gọi)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] Đã tách khỏi đợt 1; đợt 2 có 2 phase BE
- [x] Rule mới G94-180/CHD-14 chỉ ghi khi user duyệt plan
- [x] Tự review: không placeholder · không mâu thuẫn

## Progress log
### 2026-11-22 — draft, chờ user duyệt
- Commit: chưa commit
- Tiếp: làm sau khi đợt 1 xong và user duyệt plan này.

### 2026-11-22 — chưa làm
- Đợt 2 sửa cả dòng Cần viết (`ChildIntakeServiceImpl.java:640-720`, phía FE: ô năm tiếp nhận, dòng nhắc). Chờ user quyết làm hay chờ Cần.
