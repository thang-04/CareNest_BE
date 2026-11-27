# Verification — bằng chứng trước khi báo xong

## N+1 tiếp nhận trẻ và danh sách đợt khám — 27/11/2026

- Hoàn thành bốn hạng mục trong `../N+1-ke-hoach-sua-code-G94-180-2026-11-26.md`, đo trên PostgreSQL Testcontainers bằng Hibernate Statistics và StatementInspector.
- Danh sách xếp lớp tăng 3 lên 13 lớp vẫn dùng 10 SQL. Nhập 5 và 10 dòng giữ số đọc năm học ở 2 cho năm hiện tại, 3 cho năm kế tiếp, đọc điểm trường ở 3, đọc phân công ở 2.
- Tìm kỳ liên kết và tra lớp hiện tại cho 8 đợt cùng ngày giảm từ 8 xuống 1 truy vấn cho mỗi nhóm. Ba ngày tham chiếu dùng 3 truy vấn tra lớp, không có kết quả dùng 0.
- Native Maven `verify` với tám class liên quan đạt 230/230 test, 0 fail/error/skip, gồm OpenAPI 4/4. Package và Spotless đạt. Log ở TEMP, tên `nplus1-combined-verify.log`. Review riêng spec và chất lượng mã không còn finding.
- Chưa benchmark độ trễ DB triển khai hoặc lô đủ 500 dòng. Số đo nhập file chỉ tính nhóm tra cứu năm, điểm trường và phạm vi, các thao tác tạo hồ sơ và kiểm trùng theo dòng tiếp tục tăng theo số dòng.

## BR hồ sơ trẻ, sức khỏe và đón trẻ — 27/11/2026

- Đã chạy `./mvnw.cmd -q verify` với Docker. Surefire chạy 2.805 test, 0 assertion failure, 15 error, 3 skip. Toàn bộ 15 error nằm trong `EduYearPlanServiceIntegrationTest`, cùng lỗi dữ liệu thử nghiệm vượt `varchar(20)`. Đây là nhóm lỗi cũ được kế hoạch yêu cầu ghi nhận.
- Các test tích hợp mục tiêu cho tiếp nhận, phiếu khám, điểm danh phụ huynh và controller đạt 168/168. Thay ảnh có 5 ca tích hợp PostgreSQL đạt cùng các test service/controller/danh sách. `OpenApiSnapshotTest` đạt 4/4 khi cập nhật snapshot.
- FE đạt 2.007/2.007 test, lint và build. App đạt 11/11 test `live_my_child_source_test.dart` và `flutter analyze lib/features/parent/my_child`.
- Log lần verify đầy đủ ở thư mục TEMP, tên `br-final-verify.log`. Plan hoàn tất ở `docs/plans/completed/2026-11-26-br-code-alignment.md`.

## Iron Law

## Bằng chứng theo làn
| Làn | Lệnh | Bằng chứng tối thiểu |
| --- | --- | --- |
| S | `node scripts/verify.mjs --quick` | Dòng `VERIFY PASS quick …` |
| M | `node scripts/verify.mjs` | `VERIFY PASS full …`, `skip 0` |
| L | như M + bước kiểm trong plan | `VERIFY PASS full`, skip 0 + diff snapshot (nếu đổi API) + e2e `x/y` |
| Bug fix (mọi làn) | regression test | Output **fail trước fix** (khớp triệu chứng) + **pass sau fix**; tên test ghi vào incident (nếu có) |

## Claim → bằng chứng
| Claim | Bằng chứng |
| --- | --- |
| API không đổi | Snapshot pass (không skip) + `git diff --exit-code docs/api/openapi.yaml` |
| API đổi có chủ đích | `mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true` + diff trong báo cáo + consumer FE/APP cụ thể |
| Migration OK | Test Testcontainers chạy thật (skip 0) — Flyway + `ddl-auto: validate` |
| Kiến trúc đúng | `ArchitectureRulesTest` pass |
| Format | Spotless pass (`mvnw.cmd spotless:apply` để sửa) |
| AI layer nhất quán | `node scripts/check-ai-layer.mjs` exit 0 |
| Không dính file rác | `node scripts/ai-layer/junk-files.mjs` exit 0 (verify tự chạy; cảnh báo `[JUNK] WARN` ⇒ commit hoặc thêm `.gitignore`) |

## Gate fail
Sửa nguyên nhân. Không xóa/`@Disabled`/skip test, không nới rule ArchUnit, không `--no-verify`, không regenerate snapshot cho qua. Rule sai thật ⇒ đề xuất sửa rule + lý do, hỏi user. Snapshot đổi ngoài ý muốn = bug contract.

## Báo cáo cuối task
```text
Làn S (≤3 dòng):  Đã làm: … · Verify: VERIFY PASS quick (12 tests) · Memory: <chỉ khi có trigger>
Làn M (≤8 dòng):  Đã làm · Rule/AC · Verify (dòng VERIFY) · Chưa kiểm chứng: … · Ảnh hưởng còn chờ · Memory: …
Làn L:            ghi đủ vào Progress log của plan; chat chỉ tóm tắt + link plan
```

## Bằng chứng ghi ở đâu

Không ghi nhật ký từng task vào file này (file này chỉ chứa quy tắc, để đọc nhanh). Bằng chứng của task: làn S/M ghi trong báo cáo cuối task; làn L ghi vào Progress log của plan; khi mở PR ghi vào mục "Kiểm thử". Nhật ký cũ (trước 12/11/2026) lưu ở `docs/quality/VERIFICATION_LOG.md` — không cần đọc khi làm task.
