---
title: Xếp lớp, chuyển lớp và danh sách trẻ đầy đủ
status: in-progress
owner: G94 team
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [child, notification]
rules: [G94-180/CHD-02, G94-180/CHD-03, G94-180/CHD-06, G94-180/CHD-09, G94-180/CHD-10, CHD-04, AUTH-02, AUTH-07, AUTH-08, CMR-04]
risk: high
created: 2026-11-10
updated: 2026-11-10
---
# Xếp lớp, chuyển lớp và danh sách trẻ đầy đủ

Làn L (migration V84, API mới và đổi response `GET /children`, FE/APP ảnh hưởng). Chế độ tự chạy (`CLAUDE.md`): agent chọn phương án khuyến nghị, ghi ở "Key decisions"; `status` không tự đặt `approved`.

## Context
User yêu cầu 2026-11-10 "làm tiếp mục 1 (xếp lớp, chuyển lớp) và mục 2 (danh sách trẻ đầy đủ)" của danh sách BE còn lại nhánh G94-180. Đặc tả: plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md` mục 4.4, 4.6, 4.7, 6.2, 9 (Y-2, Y-3) và D-4. Contract FE (BE giả, module `children` còn `demo`): `FE:src/services/children/mock/childrenMockRepository.js` `placeChild` (dòng 1177–1206), `listChildren` (874–892), `getChildDetail` (895–921); trang `FE:src/pages/children/ClassPlacementPage.jsx`, `ChildrenListPage.jsx`, `ChildProfilePage.jsx`. Màn Tài khoản phụ huynh (`FE:src/pages/children/ParentActivationPage.jsx`, module `guardian` đã `api`) đang gọi `GET /children` thật và đọc `items`, `totalPages`, `classroomId`, `guardianCount`.

Hiện trạng BE: không có `POST /children/{id}/placement`, không có loại thông báo `CHILD_PLACED`; `GET /children` (`ChildController.searchChildren`, quyền `PARENT_ACCOUNT_MANAGE`) chỉ trả `ChildSummaryResponse` (id, họ tên, ngày sinh, `classroomId`, `campusId`, `guardianCount`, ảnh), không lọc năm học, trạng thái; HT thấy cả trẻ đã nghỉ, PHT không. `GET /children/{id}/profile` trả `{ child, declaration }`, chưa có `cls`, `placements`. Kiểm lớp khi tiếp nhận ở `ChildIntakeServiceImpl.placeOf`.

## Quyết định đã chốt
1. Rule CONFIRMED dùng nguyên: G94-180/CHD-02 (xếp sang lớp điểm trường khác = chuyển điểm trường, user chốt 2026-10-28), CHD-09 (danh sách theo năm học), CHD-10 (chỉ lớp năm đang hoạt động), D-4 A (một endpoint `GET /children` phân trang).
2. Lệch còn mở: CHD-03 của `dev` "không chuyển cơ sở" ⇔ G94-180/CHD-02 — chờ team; BE làm theo G94-180/CHD-02 (rule của nhánh), chặn lại chỉ cần một điều kiện.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT/PHT có `CHILD_PLACEMENT` xếp trẻ Chờ xếp lớp vào lớp, hoặc chuyển trẻ đang học sang lớp khác (bắt buộc lý do), lớp mới tính từ hôm nay; GV lớp mới nhận thông báo. Hồ sơ trẻ hiện lịch sử xếp/chuyển lớp. Danh sách trẻ: HT toàn trường, PHT điểm trường mình, GV lớp mình; lọc năm học, điểm trường, lớp, trạng thái, từ khóa; phân trang.
- **Đối chiếu FE:** BE giả trả 403 cho trẻ đã nghỉ, 422 cho lỗi ô; BE dùng 409 3011 (như Sửa hồ sơ, cho nghỉ học) và 400 + danh sách ô (C-5). BE giả không kiểm độ dài lý do — BE ≤ 300 như cho nghỉ học. Bộ lọc "mức sức khỏe" của D-4 đã bỏ ở FE (2026-10-31) ⇒ không làm.
- **Ảnh hưởng:** child (xếp lớp, danh sách, hồ sơ), notification (loại mới). FE: `childrenApi.listAllChildren` cần gửi `schoolYear` (id số), `status`, `classId`, `keyword`; đọc `classId`/`className`; adapter map `relation` mã ⇒ nhãn; màn Tài khoản phụ huynh không phải đổi (giữ `classroomId`, `guardianCount`, mặc định bỏ trẻ đã nghỉ). APP: không dùng.

### Câu hỏi mở
Không chặn; giả định ở "Key decisions".

## Acceptance criteria
- **AC-1** (CHD-10, G94-180/CHD-02): `POST /children/{id}/placement` `{ classId, reason }` (`CHILD_PLACEMENT`): trẻ Chờ xếp lớp ⇒ Đang học, lớp mới từ hôm nay; trẻ đang học ⇒ lớp cũ kết thúc hôm qua (vào lớp cũ hôm nay thì bỏ lớp cũ), lớp mới từ hôm nay; lớp điểm trường khác ⇒ điểm trường đổi theo lớp. Trả ChildView.
- **AC-2**: lỗi ô (400 9001): thiếu `classId` "Chọn lớp cho trẻ"; cùng lớp "Trẻ đang học lớp này. Chọn lớp khác."; đang có lớp mà thiếu `reason` "Nhập lý do chuyển lớp", quá 300; chưa khai báo sức khỏe (ô `classId`); lớp ngừng hoạt động, khác năm đang hoạt động, ngoài điểm trường được giao, đủ sĩ số — câu như tiếp nhận. Trẻ đã nghỉ ⇒ 409 3011; ngoài phạm vi ⇒ 403.
- **AC-3** (mục 6.2): xếp/chuyển lớp và tiếp nhận có lớp ⇒ `CHILD_PLACED` "Lớp có trẻ mới" — "{tên} đã được xếp vào {lớp}." cho GV lớp mới; ghi lịch sử xếp lớp (lớp cũ, lớp mới, người, lúc, lý do).
- **AC-4** (mục 4.6): `GET /children/{id}/profile` thêm `cls` (lớp hiện tại; trẻ đã nghỉ: lớp cuối; chờ xếp lớp: null — id, tên, điểm trường, nhóm tuổi, năm học, sĩ số, trạng thái, GV chủ nhiệm, GV lớp) và `placements[]` mới nhất trước.
- **AC-5** (D-4, CHD-09): `GET /children` thêm lọc `schoolYear`, `status` (nhiều giá trị; mặc định Đang học + Chờ xếp lớp), `classId`, `keyword` (họ tên hoặc mã); mỗi dòng có thêm mã, giới tính, trạng thái, `classId`, `className`, `ageGroupId`, `schoolYearId`, ngày tiếp nhận/nghỉ, dị ứng, `hasDeclaration`, `guardians[]`, `canManage`; giữ `classroomId`, `guardianCount`, `items`/`totalPages`. Có `schoolYear`: lớp cuối của trẻ trong năm đó; Chờ xếp lớp chỉ ở năm đang hoạt động; Đã nghỉ chưa có lớp chỉ ở năm của ngày nghỉ.
- **AC-6** (AUTH-04, AUTH-07, AUTH-08): HT toàn trường; PHT điểm trường được giao (theo lớp hiển thị, CMR-04); GV lớp mình đang dạy; phụ huynh 403; lọc lớp/điểm trường ngoài phạm vi ⇒ 403 như hiện có.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lưu lịch sử xếp lớp | Bảng mới `child_placements` (V84), chỉ thêm | Thêm action `PLACED` vào `child_history` | FE tách tab "Lịch sử hồ sơ" (4 action) và tab xếp lớp; giữ nghĩa `child_history`; bảng khớp `childPlacements` của BE giả |
| Ngày hiệu lực | Lớp mới từ hôm nay; lớp cũ kết thúc hôm qua, vào lớp cũ hôm nay thì bỏ lớp cũ (như cho nghỉ học) | Từ ngày mai | Spec gợi ý hôm nay; toast FE đọc `className` mới ngay |
| Trẻ đã nghỉ | 409 3011 | 403 như BE giả | Đồng bộ Sửa hồ sơ, cho nghỉ học |
| Chuyển điểm trường | Cho phép khi người làm quản lý cả điểm trường cũ (trẻ) và mới (lớp) — PHT một điểm trường không chuyển được | Chặn theo CHD-03 `dev` | G94-180/CHD-02 user chốt; lệch `dev` chờ team |
| Điểm trường tiếp nhận khi xếp lớp | Xóa (như tiếp nhận có lớp) | Giữ | `campusOf` của trẻ đã nghỉ ưu tiên điểm trường tiếp nhận ⇒ giữ sẽ sai sau khi chuyển điểm trường |
| `GET /children` | Mở rộng endpoint hiện có, giữ trường cũ, mặc định bỏ trẻ đã nghỉ | Endpoint mới | D-4 A một endpoint; màn Tài khoản phụ huynh (đang chạy thật) không vỡ |
| Phân trang danh sách | Một truy vấn native trả id + lớp hiển thị đã lọc, sắp xếp; cắt trang ở service; nạp chi tiết theo lô cho trang | Native query + count query | Quy mô một trường (vài trăm – vài nghìn trẻ); lọc năm học cần lớp suy diễn, sắp theo tên lớp |
| Phạm vi PHT theo năm | Theo điểm trường của lớp hiển thị (lớp trong năm đó) | Theo điểm trường hiện tại như BE giả | G94-180/CHD-02 "dữ liệu cũ theo lớp tại ngày đó (CMR-04)" |
| Phụ huynh trong danh sách | Như hồ sơ nhưng bỏ CCCD, email | Đủ như hồ sơ | AUTH-03 tối thiểu dữ liệu; màn danh sách chỉ đọc họ tên, quan hệ, SĐT, trạng thái tài khoản |
| Từ khóa | `lower(...) LIKE` họ tên hoặc mã (phân biệt dấu) | `unaccent` | DB chưa có extension; ghi giới hạn |
| Lọc mức sức khỏe | Không làm | Làm theo D-4 | FE đã bỏ 2026-10-31; module child không đọc health (tránh cycle) |

## Phases
### Phase 1 — Xếp lớp, chuyển lớp, hồ sơ
- C `V84__child_placements.sql`, `ChildPlacement(+Repository)`, `PlaceChildRequest`, `ChildPlacementResponse`, `ChildClassResponse`, `PlacementRules` (kiểm lớp dùng chung với tiếp nhận, ghi lịch sử + thông báo); M `ChildRecordService(+Impl)` (`place`, `findClass`, `findPlacements`), `ChildIntakeServiceImpl` (dùng `PlacementRules`, ghi lịch sử + báo khi tiếp nhận có lớp), `Child.placeInClass`, `ChildEnrollmentController`, `ChildPhotoProfileResponse`, `ChildPhotoController`, `NotificationType`.
### Phase 2 — Danh sách trẻ
- C `ChildListItemResponse`, `ChildListFilter`; M `ChildRepository` (native query), `ChildGuardianRepository` (đọc theo lô), `ChildService(+Impl)` (`listChildren` thay `searchChildren`), `ChildController`.
- Exit mọi phase: unit + WebMvc + integration (PostgreSQL); OpenAPI snapshot; `node scripts/verify.mjs` full.

## Test matrix
| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | CHD-10, G94-180/CHD-02 | `ChildRecordServiceImplTest#transferEndsOldClassAndStartsNewOne`, `#placingPendingChildNeedsNoReason`; `ChildPlacementListIntegrationTest#placePendingChild`, `#transferTwiceSameDay` (lớp cũ hết hôm qua, chuyển lại trong ngày bỏ lớp vừa vào), `#crossCampusTransfer`; `ChildEnrollmentControllerTest#placementManagerPlacesChild` | 1 | pass |
| AC-2 | CHD-10, CHD-06 | `ChildRecordServiceImplTest#placeValidatesFields`, `#placingLeftChildIsRejected`; `ChildPlacementListIntegrationTest#crossCampusTransfer` (PHT sang điểm trường khác ⇒ ô `classId`); `ChildEnrollmentControllerTest#placementNeedsPlacementPermission` | 1 | pass |
| AC-3 | mục 6.2 | `ChildPlacementListIntegrationTest#placePendingChild`, `#transferTwiceSameDay` (thông báo GV lớp mới, lịch sử); `ChildEnrollmentIntegrationTest#enrollIntoClassChecksClass` (tiếp nhận có lớp ghi lịch sử, báo GV) | 1 | pass |
| AC-4 | mục 4.6 | `ChildPlacementListIntegrationTest#transferTwiceSameDay` (`findClass` có GV lớp, `findPlacements` mới nhất trước); `ChildPhotoControllerTest#profileIncludesHealthDeclaration` | 1 | pass |
| AC-5 | D-4, CHD-09 | `ChildPlacementListIntegrationTest#listFollowsSchoolYearRules`; `ChildListServiceImplTest#defaultsAndScopeReachQuery`, `#pagesAndMapsRows` (trang, CCCD/email bỏ, giữ `classroomId`, `guardianCount`); `ChildControllerTest#teacherListsChildrenWithLegacyParams`; `GuardianDirectoryIntegrationTest#listRowsFollowScopeAndKeyword` | 2 | pass |
| AC-6 | AUTH-07, AUTH-08 | `ChildListServiceImplTest#filtersOutsideScopeAreDenied`, `#teacherRowsAreReadOnly`; `ChildPlacementListIntegrationTest#teacherScopeAndKeyword`, `#crossCampusTransfer` (PHT cũ hết thấy, năm cũ theo lớp lúc đó); `ChildControllerTest#kitchenCannotListChildren`, `ChildListServiceImplTest#parentIsForbidden` | 2 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Chuyển lớp giữa ngày khi lớp cũ đã điểm danh hôm nay | Bản ghi điểm danh hôm nay ở lớp cũ, sĩ số lớp mới có trẻ | Ghi pitfall; GV lớp mới điểm danh lại nếu cần | — |
| Nhập Excel nhiều trẻ có lớp | GV nhận nhiều thông báo `CHILD_PLACED` | Mỗi trẻ một thông báo, như BE giả | — |
| FE gửi `schoolYear` là tên năm | 400 sai kiểu | Ghi CROSS_REPO: FE map tên ⇒ id | — |

## Validation log
- [x] Khẳng định về code có `file:line` (Context: BE giả FE theo dòng; BE `ChildIntakeServiceImpl.placeOf`, `ChildController.searchChildren`)
- [x] Đổi contract ⇒ consumer cụ thể: FE `ClassPlacementPage` (POST placement, đọc `fullName`, `className`), `ChildProfilePage` (`cls`, `placements[]`), `childrenApi.listAllChildren` (gửi `schoolYear` id, `status`, `classId`, `keyword`; đọc `items`, `totalPages`), màn Tài khoản phụ huynh (`guardianApi`, giữ nguyên)
- [x] Mỗi AC có test; Exit có lệnh (`node scripts/verify.mjs`)
- [x] Không dùng rule PENDING như đã chốt (CHD-03 `dev` lệch ghi ở Quyết định đã chốt mục 2)

## Progress log
- 2026-11-10: gộp `dev` vào nhánh trước (commit `0a41316`, user cho phép); Phase 1–2 xong: V84 (`child_placements`, `CHILD_PLACED`), `POST /children/{id}/placement`, `cls`, `placements[]` ở hồ sơ, tiếp nhận có lớp ghi lịch sử + báo GV, `GET /children` đủ bộ lọc (`ChildListService`; bỏ `ChildService.searchChildren`, `ChildSummaryResponse`, truy vấn `searchForParentAccounts`). Docs: card child, notification; ERROR_CONTRACT; DATA_DICTIONARY; CROSS_REPO_MAP; OpenAPI snapshot. FE cần nối (module `children` còn demo).
- 2026-11-11: gộp `origin/dev` (có `V83__consolidate_boarding_tables`, không có V82) ⇒ đổi số migration của nhánh: V84 `child_placements` → V86. Nội dung migration không đổi.
