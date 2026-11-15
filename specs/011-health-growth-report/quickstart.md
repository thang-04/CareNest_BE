# Quickstart / bằng chứng — Feature011

## BE (04/11/2026, branch `feature/G94-180-BE-FEAT-43-child-health-pickup`, chưa commit, trên `ff66f7d`)
- Sau sửa source/test cuối (gồm sửa theo review độc lập): `node scripts/verify.mjs` → `VERIFY PASS full | tests 1055 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 477s | log target/verify.log`. Lượt trước review: `tests 1050 … 465s`.
- Test liên quan trong lượt cuối, không skip: `WhoWeightHeightForAgeTest` 26, `HealthGrowthLevelTest` 2, `HealthGrowthReportIntegrationTest` 5 (Testcontainers PostgreSQL 17), `HealthGrowthReportControllerTest` 8 (WebMvc), `WhoBmiForAgeTest` 33 (giữ nguyên sau khi tách `WhoLmsTable`), `OpenApiSnapshotTest` 3.
- Snapshot OpenAPI cập nhật có chủ đích (`-Dopenapi.snapshot.update=true`), chỉ thêm: path `/api/health-checks/growth-report` (operation `getGrowthReport`), schema `HealthGrowthReportResponse`, `HealthGrowthCampus`, `HealthGrowthCampaign`, `HealthGrowthClass`, `HealthGrowthCounts`, `ResponseJsonHealthGrowthReportResponse`. Không đổi API cũ, không migration.
- Bảng WHO: 10 tệp z-score Excel từ who.int, ghép tháng 0–84 (W/A: 2006 0–60, 2007 61–84; H/A: chiều dài 0–23, chiều cao 24–60, 2007 61–84). Mỗi dòng: −2SD tính lại từ L, M, S khớp cột SD2neg của WHO. Test SD công bố: sai số 0,05 (2006), 0,001 (2007).
- Số liệu trẻ giả trong test tích hợp: z của từng trẻ tính lại độc lập bằng Node (công thức LMS) trước khi viết kỳ vọng; reviewer tính lại cả 11 giá trị, đều khớp.
- Review độc lập (subagent chỉ đọc, 1 lượt): không P1/P2; 11 P3, sửa 10 (test làm tròn ranh giới, test đuôi, trẻ chuyển lớp, `levelOf`, PHT với id không tồn tại, HLT-18/HLT-20, giả định BMI/tuổi, giới hạn kế thừa ở card, điểm trường ngừng hoạt động, tài liệu plan/tasks, chú thích tuổi); giữ 1 (hiệu năng, chấp nhận ở quy mô trường).
- `node scripts/check-ai-layer.mjs` → `check-ai-layer (BE): 1 lỗi, 12 cảnh báo · E1 ok · E2 12 · E3 ok · E4 ok · E5 1 · E6 bỏ qua · E7 ok`. Lỗi duy nhất: link FE ở `BUG-261102` (có từ trước, ngoài task).

## Chưa kiểm chứng
- Chưa chạy Web nối BE thật (FE làm màn báo cáo với BE giả, agent FE).
- Cột "cân nặng/chiều cao" của mẫu tính bằng BMI/tuổi: giả định chờ trường xác nhận (HLT-20 PROPOSED).
- APP không gọi API này. Không commit/push.
