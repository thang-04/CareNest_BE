# Quickstart / bằng chứng — Feature010

## BE (04/11/2026, branch `feature/G94-180-BE-FEAT-43-child-health-pickup`, chưa commit)
- Sau sửa source cuối (gồm sửa theo review độc lập): `node scripts/verify.mjs` → `VERIFY PASS full | tests 999 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 471s | log target/verify.log`. Lượt trước review: `tests 985 … 482s`.
- Test liên quan chạy riêng: 92/92 (`WhoBmiForAgeTest` 33, `HealthExamIntegrationTest` 7 — Testcontainers PostgreSQL 17, `HealthCheckIntegrationTest` 11, `HealthCheckServiceImplTest` 6, `HealthExamLogicTest` 32, `OpenApiSnapshotTest` 3), không skip.
- Snapshot OpenAPI cập nhật có chủ đích (`-Dopenapi.snapshot.update=true`): bỏ 6 path `/health-follow-ups/**` và schema phiếu theo dõi; thêm `HealthLatestExam`, `HealthLatestBmi`, `HealthBmiProgress`, trường `bmiStatus`, `bmiZScore`, `bmi`, `bmiWarning`.
- Review độc lập (subagent): 1 P1 (nguồn "vấn đề đã biết" mất cùng phiếu theo dõi) đã sửa + test AC-6; P2 method chết, thiếu test mẫu FOLLOW_UP cũ đã sửa; P3 (z làm tròn trước khi xếp mức, BOM/LF bảng WHO, Javadoc/tài liệu cũ, @DisplayName) đã sửa. Giữ: tuổi tháng tròn (không nội suy theo ngày), `latestBmi` = lần gần nhất xếp được mức (HLT-19 ghi rõ).
- Bảng WHO đối chiếu: 170 dòng L, M, S khớp bảng z-score Excel WHO; reviewer đối chiếu thêm với `bmianthro.txt`/`bfawho2007.txt` của WHO (61–84 tháng khớp; 0–60 tháng lệch ≤ 0,0003 do bảng theo tháng).

## Chưa kiểm chứng
- Chưa chạy UI Web nối BE thật (module sức khỏe Web vẫn dùng BE giả `children: demo`).
- APP chưa gọi API sức khỏe; chưa có màn phụ huynh xem mức BMI.
- Không commit/push, không migration, không xóa dữ liệu phiếu cũ.

## Bỏ câu "vấn đề mới hay đã biết" (04/11/2026, chưa commit)
- `node scripts/verify.mjs` → `VERIFY PASS full | tests 999 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 522s`; test sức khỏe chạy riêng 89/89 (`HealthExamIntegrationTest#healthIssueIsWatchedWithoutAskingIfKnown`, `HealthExamLogicTest#issueKindIsNeverRequired`). Snapshot OpenAPI chỉ bỏ `knownCondition` ở phiếu khám và `latestExam`.
