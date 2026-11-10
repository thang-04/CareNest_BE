---
title: Báo cáo tổng hợp tăng trưởng theo tháng (mẫu 5.1) cho HT/PHT
status: in-progress   # chế độ tự chạy (CLAUDE.md, user chốt 2026-10-23): plan tự viết rồi làm tiếp, user soát khi bàn giao
owner: user (CareNest team)
jira: G94-180 / BE-FEAT-43 (nhánh hiện tại; task Jira riêng cho báo cáo chưa có — hỏi khi commit)
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [health]
rules: [HLT-20, HLT-03, HLT-10, HLT-18, HLT-01, HLT-08, HLT-17]
risk: high   # API mới FE dùng; dữ liệu sức khỏe theo phạm vi điểm trường; không migration
created: 2026-11-04
updated: 2026-11-04
---
# Báo cáo tổng hợp tăng trưởng theo tháng (mẫu 5.1) cho HT/PHT

Spec Kit: `specs/011-health-growth-report/` (domain spec, spec, research, data model, contract, tasks, quickstart). FE làm song song theo contract ở `docs/system/CROSS_REPO_MAP.md` (agent FE).

## Context
User 2026-11-04: cho HT (toàn bộ phân hiệu) và PHT (phân hiệu được phân công) bảng tổng hợp theo mẫu báo cáo "5.1" của trường, mỗi tháng một khối: hàng là lớp theo nhóm tuổi (5 tuổi, 4 tuổi, 3 tuổi, Tổng MG, Nhà trẻ, Tổng NT, Tổng toàn trường); cột TS trẻ của lớp, TS trẻ được cân đo, tỉ lệ; theo dõi cân nặng (PTBT, SDDNC), chiều cao (PTBT, SDDTC), cân nặng/chiều cao (BT, SDD thể gầy còm, thừa cân, béo phì), mỗi chỉ số tổng số và tỷ lệ. "Hãy thiết kế sao cho phù hợp". Trước đó (user 2026-10-31, `docs/plans/completed/2026-10-31-health-check-rounds.md:28`) hoãn báo cáo 5.1, 5.3 và phân loại WHO cho báo cáo ⇒ nay mở 5.1; 5.3 vẫn hoãn.

Hiện trạng: chỉ có BMI theo tuổi (`src/main/java/com/carenest/service/health/impl/WhoBmiForAge.java`, bảng `src/main/resources/health/who-bmi-for-age.csv`) cho số đo kỳ cân đo (HLT-18); W/A, H/A "hoãn" (HLT-10); danh sách lớp của đợt/kỳ ở `HealthCheckRules.classesOf`/`rosterOf`; phạm vi HT/PHT ở `HealthCheckRules.canManage`.

## Quyết định đã chốt
1. Báo cáo theo mẫu 5.1, HT mọi điểm trường, PHT điểm trường được phân công — user, 2026-11-04.
2. Chỉ số theo chuẩn WHO (HLT-03): cân nặng/tuổi, chiều cao/tuổi, BMI/tuổi; SDDNC, SDDTC là z < −2 — user (mẫu), 2026-11-04; người điều phối giao các quy tắc đếm ở Key decisions.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT, PHT xem báo cáo tháng · outcome: bảng 5.1 theo lớp, tổng MG/NT/toàn trường, không phải nhập tay · HLT-20 (mới, PROPOSED), HLT-10 sửa, HLT-03, HLT-18 · `[S]` tính khi đọc từ kết quả đã có.
- **Đối chiếu:** HLT-10 cũ "Phân loại WHO khác (cân nặng/tuổi, chiều cao/tuổi) hoãn" (`docs/business/BUSINESS_RULES.md:141`) ⇒ dùng cho báo cáo, sổ sức khỏe giữ nguyên. Báo cáo 5.1 hoãn 31/10 ⇒ user mở 04/11.
- **Ảnh hưởng:** module health · quyền: HT/PHT theo `canManage`, GV không xem · dẫn xuất tính khi đọc, không lưu · FE: màn báo cáo mới (agent FE) · APP: không · dữ liệu cũ: số đo kỳ cân đo cũ, phiếu khám đã kết luận đều dùng được.

### Câu hỏi mở
- [x] Trẻ "được cân đo" gồm phiếu khám? → có, phiếu khám đã kết luận có chiều cao, cân nặng (người điều phối, theo mẫu dùng mọi lần cân).
- [x] Nguy cơ thừa cân (HLT-18) ở cột nào? → BT (mẫu không có cột nguy cơ).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Phạm vi | HT toàn bộ phân hiệu, PHT phân hiệu được phân công | user | 2026-11-04 | HLT-20 |
| Đếm, ánh xạ cột | Như Key decisions | người điều phối (brief) | 2026-11-04 | HLT-20 |

## Acceptance criteria
- **AC-1** (`HLT-20`, `HLT-03`): Given số đo trẻ 0–84 tháng When xếp W/A, H/A Then z theo LMS WHO làm tròn 2 chữ số; W/A giới hạn đuôi ngoài ±3SD, H/A công thức thường; < −3 nặng, < −2 nhẹ; ngoài 0–84 tháng hoặc thiếu ngày sinh/giới tính ⇒ null.
- **AC-2** (`HLT-20`): Given HT When gọi báo cáo tháng Then mọi điểm trường (lọc `campusId`); PHT chỉ điểm trường được phân công, `campusId` khác (kể cả không tồn tại) ⇒ 403 5010; vai trò khác ⇒ 403 (HTTP 1101, gọi service 5010); `month` thiếu/sai ⇒ 400 9002; điểm trường ngừng hoạt động không có đợt/kỳ bị ẩn trừ khi lọc đúng nó.
- **AC-3** (`HLT-20`): Given đợt/kỳ chưa hủy có ngày bắt đầu trong tháng When gọi Then đếm trẻ theo danh sách lớp của đợt/kỳ, hợp theo trẻ; được cân đo = số đo kỳ cân đo hoặc phiếu khám đã kết luận có chiều cao, cân nặng (lấy lần mới nhất); nháp, vắng không tính; điểm trường không có đợt/kỳ vẫn trả với 0 (trừ điểm trường ngừng hoạt động, xem AC-2).
- **AC-4** (`HLT-20`): ánh xạ cột, `unclassified`, thứ tự lớp (MG theo nhóm tuổi giảm dần, NT, lớp không nhóm tuổi), tổng MG/NT/điểm trường/toàn trường đúng.
- **AC-5** (contract): `GET /health-checks/growth-report` có trong snapshot OpenAPI; sổ sức khỏe, cảnh báo không đổi.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nơi đặt API | `GET /health-checks/growth-report` (`HealthCheckController`) + service riêng `HealthGrowthReportService` | Thêm vào `HealthCheckServiceImpl` | Cùng nhóm đợt/kỳ; không làm service đã ~740 dòng lớn thêm |
| Nguồn dữ liệu | Danh sách lớp của đợt/kỳ (`classesOf`, `rosterOf`) | Đếm trẻ đang học theo enrollment ngày cuối tháng | Khớp số ở màn đợt/kỳ (vắng, nháp, chưa kết luận) |
| Trẻ ở nhiều đợt/kỳ trong tháng | Hợp theo trẻ trong từng điểm trường; lấy lần đo được tính mới nhất (ngày, rồi id); lớp = lớp của lần đo đó, chưa đo thì lớp ở đợt/kỳ mới nhất | Đếm theo từng đợt/kỳ | Mỗi trẻ một dòng như mẫu |
| Tổng toàn trường | Cộng các điểm trường | Hợp trẻ toàn trường | Khớp các bảng điểm trường; trẻ chuyển điểm trường trong tháng có thể tính ở cả hai (ghi rõ) |
| BMI trong báo cáo | Tính cả cho phiếu khám (`WhoBmiForAge`) | Bỏ phiếu khám | Mẫu đếm mọi trẻ được cân; sổ sức khỏe giữ nguyên (HLT-18) |
| Nhóm MG/NT | Theo `ageFromMonths` của nhóm tuổi: < 36 ⇒ NT, ≥ 36 ⇒ MG; thiếu tháng thì theo mã `ag-2` NT, `ag-3..5` MG; không nhóm ⇒ null | Chỉ theo mã | Nhóm tuổi HT cấu hình được (mã tùy ý); nhà trẻ là dưới 3 tuổi |
| Thang z | Làm tròn 2 chữ số rồi so ngưỡng; tuổi tháng tròn tại ngày đo; < 24 tháng bảng đo nằm | Nội suy theo ngày | Như BMI (HLT-18), bảng WHO theo tháng |
| Lỗi tham số | `month` `YearMonth` (`@DateTimeFormat yyyy-MM`) ⇒ thiếu/sai 400 9002; HT `campusId` không có ⇒ 404 `CAMPUS_NOT_FOUND`; PHT ngoài phạm vi (kể cả không tồn tại, không lộ id có hay không) ⇒ 403 5010; vai trò khác ⇒ 403 1101 (`@PreAuthorize`), gọi service trực tiếp ⇒ 5010 | 400 theo ô | Như báo cáo chất lượng (`QualityReportController`) và đợt/kỳ |
| Phần trăm | Không trả, FE tính | Trả % | Tránh lệch làm tròn hai nơi; brief |
| Cột "cân nặng/chiều cao" của mẫu | BMI/tuổi WHO (brief) — **giả định chờ trường xác nhận** | Cân nặng theo chiều dài/chiều cao WHO (trẻ < 5 tuổi) | Brief chỉ định BMI, đã có bảng; trẻ sát ngưỡng có thể khác nhóm giữa hai cách |
| Điểm trường ngừng hoạt động | Ẩn khi tháng không có đợt/kỳ; hiện khi có, hoặc khi lọc đúng nó | Luôn hiện khối số 0 | `INACTIVE` chỉ còn dữ liệu cũ; tránh khối rỗng mỗi tháng (review P3) |
| Giới hạn kế thừa | Ghi ở card: nhóm tuổi đọc cấu hình hiện tại; đợt/kỳ kéo qua năm học mới chỉ lớp năm bắt đầu | Đọc bản chụp năm học; chặn đợt kéo qua năm | Giống màn đợt/kỳ; ngoài phạm vi báo cáo |

## Phases
### Phase 1 — BE + tri thức
- Files: C `who-weight-for-age.csv`, `who-height-for-age.csv`, `WhoLmsTable`, `WhoWeightForAge`, `WhoHeightForAge`, `WeightForAgeStatus`, `HeightForAgeStatus`, `HealthGrowthLevel`, `HealthGrowthReportResponse`, `HealthGrowthReportService(Impl)`, test `WhoWeightHeightForAgeTest`, `HealthGrowthReportIntegrationTest` · M `WhoBmiForAge`, `HealthCheckController`, docs, `docs/api/openapi.yaml`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0; check-ai-layer không thêm lỗi.
### Phase 2 — FE (agent FE, song song)

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | HLT-20, HLT-03 | `WhoWeightHeightForAgeTest` (unit), `HealthGrowthLevelTest` | 1 | pass 26/26, 2/2 |
| AC-2..4 | HLT-20 | `HealthGrowthReportIntegrationTest` (Testcontainers), `HealthGrowthReportControllerTest` (WebMvc) | 1 | pass 5/5, 8/8 |
| AC-5 | HLT-20 | `OpenApiSnapshotTest` | 1 | pass 3/3 (snapshot chỉ thêm) |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Sai bảng WHO | Đếm SDD sai | Chép từ Excel WHO, kiểm −2SD với cột SD của WHO từng dòng, test SD công bố | Sửa CSV |
| Nhiều điểm trường, nhiều lớp | Chậm | Đọc lần đo, vắng của mọi đợt/kỳ trong tháng một lần cho cả báo cáo, thông tin trẻ một lần; sĩ số theo lớp như màn đợt/kỳ (review: chấp nhận ở quy mô trường) | — |
| Lộ dữ liệu điểm trường khác | PHT thấy ngoài phạm vi | `canManage` + test 403 | Gỡ endpoint |

## Validation log
- [x] Khẳng định về code có `file:line` (Context)
- [x] Đổi contract ⇒ consumer: FE màn báo cáo mới (agent FE); APP không
- [x] Mỗi AC có test; phase có Exit
- [x] >8 file: một phase BE (báo cáo + bảng + test gắn chặt), FE tách
- [x] Không dùng rule PENDING/OPEN như đã chốt (HLT-20 PROPOSED, ghi nguồn)
- [x] Tự review: không placeholder, không mâu thuẫn

## Progress log
### 2026-11-04 — Phase 1 (BE xong, chờ FE và commit)
- Commit: commit, push lên nhánh feature, mã G94-180 (user cho phép 2026-11-04; chưa có Jira riêng cho báo cáo).
- Bảng WHO: tải 10 tệp z-score Excel từ who.int (wfa 0–5 tuổi, lhfa 0–2/2–5 tuổi, 2007 wfa 5–10 tuổi — trên who.int tên tệp `hfa-…-z-who-2007-exp_….xlsx` nhưng nội dung là cân nặng, đã kiểm M 18,5057 kg tháng 61 —, 2007 hfa 5–19 tuổi); ghép tháng 0–84; mỗi dòng kiểm −2SD tính từ L, M, S khớp cột SD2neg của WHO. Đã báo "main" (WHO CSV ready).
- Phân loại: `WhoLmsTable` dùng chung (đọc CSV, kiểm đủ tháng 0–84 hai giới, L ≠ 0); `WhoBmiForAge` giữ hành vi; thêm `WhoWeightForAge` (giới hạn đuôi ngoài ±3SD), `WhoHeightForAge` (công thức thường, L = 1); z làm tròn 2 chữ số HALF_UP trước khi so ngưỡng.
- API: `GET /health-checks/growth-report` trong `HealthCheckController`, service chỉ đọc `HealthGrowthReportServiceImpl`. Các lựa chọn tự chạy ghi ở Key decisions; Spec Kit `specs/011-health-growth-report/` làm theo AGENTS.md (brief không nhắc).
- Verify lượt 1 (trước review): `VERIFY PASS full | tests 1050 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 465s | log target/verify.log`.
- Review độc lập (subagent chỉ đọc, 1 lượt): không P1/P2, 11 P3. Đã sửa: test làm tròn ranh giới −2,004/−3,004 (#1); test đuôi so W/A với công thức thường trên dòng L ≠ 1, H/A chỉ khẳng định L = 1 (#2); test trẻ chuyển lớp tính một lần ở lớp của lần đo mới nhất, `HealthGrowthLevelTest`, PHT với `campusId` không tồn tại ⇒ 403 (#3); HLT-18 ghi "(trừ báo cáo tăng trưởng HLT-20)", HLT-20 nói rõ BMI cho phiếu khám đã kết luận (#4); giả định BMI/tuổi cho cột "cân nặng/chiều cao" (#5); giới hạn kế thừa ghi ở card health (#6, #7); điểm trường `INACTIVE` không có đợt/kỳ trong tháng bị ẩn, trừ khi lọc đúng nó — code, test, contract (#8); câu lỗi vai trò ở plan, tasks, quickstart (#10); chú thích 106 tháng (#11). Giữ #9 (hiệu năng): mỗi điểm trường tối đa một đợt/kỳ chưa đóng, chấp nhận ở quy mô trường.
- Verify lượt 2 (sau sửa source/test cuối): `VERIFY PASS full | tests 1055 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 477s | log target/verify.log`. Test liên quan: `WhoWeightHeightForAgeTest` 26, `HealthGrowthLevelTest` 2, `HealthGrowthReportIntegrationTest` 5, `HealthGrowthReportControllerTest` 8, `WhoBmiForAgeTest` 33, `OpenApiSnapshotTest` 3; skip 0.
- check-ai-layer: `check-ai-layer (BE): 1 lỗi, 12 cảnh báo · E1 ok · E2 12 · E3 ok · E4 ok · E5 1 · E6 bỏ qua · E7 ok` — lỗi duy nhất là link FE ở `BUG-261102` (có từ trước, ngoài task).
- Còn lại: Phase 2 FE (agent FE), converge T009; user/trường soát giả định HLT-20 (PROPOSED). Chưa commit.
