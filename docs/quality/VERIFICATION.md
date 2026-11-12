# Verification — bằng chứng trước khi báo xong

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
