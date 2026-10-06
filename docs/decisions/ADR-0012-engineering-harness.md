# ADR-0012 — Engineering harness: làn S/M/L, bằng chứng kiểm chứng, cổng tự động

- Status: ACCEPTED
- Date: 2026-10-06
- Liên quan: NFR-MAINT-02; ADR-0001 mục Hệ quả (ArchUnit "nếu nhóm đồng ý"); plan `docs/plans/active/2026-10-06-engineering-harness.md`. Tham khảo quy trình ClaudeKit Engineer (Iron Law, plan/phase, fix gate, review) và haren (plan gate, truy vết rule → test, Key decisions, Progress log).

## Bối cảnh
Agent viết phần lớn code. Lớp AI (`.ai/`, `docs/`) giúp tìm đúng chỗ nhưng chưa chặn sai: báo xong không có bằng chứng, đổi contract/migration không plan, nghiệp vụ mơ hồ bị tự diễn giải, ranh giới layer chỉ dựa vào review (ADR-0001), format không thống nhất, test tích hợp skip khi tắt Docker vẫn bị coi là pass. Mọi cổng đều là chữ. Không có CI. Quy trình đầy đủ cho mọi task lại tốn token vô ích ở task nhỏ.

## Quyết định
1. **Làn S/M/L** quyết định đọc gì, có plan không, verify thế nào, báo cáo dài bao nhiêu (`AGENTS.md`). Làn L (migration, security, integration, contract, dependency/hạ tầng, ≥2 module, FE/APP, >8 file) ⇒ plan file được user duyệt (`.ai/workflows/plan-change.md`).
2. **Cổng làm rõ nghiệp vụ** (`.ai/workflows/clarify-business.md`): task nghiệp vụ chưa rõ / PENDING / lệch tài liệu ⇒ hỏi user tới khi rõ, ghi T1 để không hỏi lại.
3. **Iron Law** (`docs/quality/VERIFICATION.md`): không báo xong khi chưa có output verify trong lượt, sau lần sửa cuối; skip = chưa kiểm chứng. `scripts/verify.mjs` tóm tắt output Maven ≤10 dòng.
4. **Cưỡng chế tất định**: Claude hooks (`.claude/hooks/`), git hooks (`.githooks/`, bật bằng `git config core.hooksPath .githooks`), `scripts/check-ai-layer.mjs`. Thiếu `Refs:` chỉ cảnh báo.
5. **Cổng Maven** trong `./mvnw verify`: ArchUnit (`ArchitectureRulesTest`), snapshot OpenAPI (`docs/api/openapi.yaml`, `OpenApiSnapshotTest`), Spotless + palantir-java-format (`ratchetFrom origin/main`). `.gitattributes` LF.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| CI (GitHub Actions) | Chặn ở server | Nhóm chưa chọn; để sau |
| Chỉ quy trình trong docs | Rẻ | Agent bỏ qua, không chặn |
| Quy trình đầy đủ cho mọi task | Đồng nhất | Tốn token/thời gian ở task nhỏ |
| JaCoCo coverage gate | Có số liệu | Khuyến khích test hình thức; để sau |
| Spotless toàn repo một lần | Đồng nhất ngay | Diff lớn, xung đột `feature/authentication` |
| springdoc-openapi-maven-plugin | Không cần test | Phải chạy app + DB trong build |

## Hệ quả
- "Xong" = có dòng `VERIFY PASS` phù hợp làn; Docker tắt ⇒ báo "chưa kiểm chứng".
- Đổi API ⇒ regenerate snapshot có chủ đích; diff là bằng chứng tác động FE/APP.
- Nới rule ArchUnit cần lý do + user duyệt. File Java bị đụng phải đúng palantir format (cả file).
- Merge `feature/authentication`: 1 commit `style` (spotless:apply) + regenerate snapshot + frontmatter cho plan Keycloak.
- Hook chỉ chạy khi mở Claude **bên trong** repo; git hooks cần mỗi người bật 1 lần.

## Còn chờ
CI; mở rộng Spotless ra toàn repo khi ổn định; rule thứ tự lớp MODULE_MAP L0–L5 khi feature hết PROPOSED; cổng build FE/APP khi có code.
