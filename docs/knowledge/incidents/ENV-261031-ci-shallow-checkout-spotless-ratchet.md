---
id: ENV-261031-ci-shallow-checkout-spotless-ratchet
type: env
modules: [deployment]
rules: []
status: fixed
date: 2026-10-31
keywords: [github actions, spotless, ratchetFrom, No such reference, fetch-depth, shallow clone, Deploy VPS]
similar_to: []
---

# ENV-261031-ci-shallow-checkout-spotless-ratchet — CI verify fail "No such reference" dù mọi test pass

## Symptom
Workflow Deploy VPS (lần đầu chạy sau khi merge PR #4 vào `dev`, run 41099172282) fail ở bước `node scripts/verify.mjs`, job `deploy` bị skip:
```text
VERIFY FAIL full | tests 611 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 264s
- Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:3.10.3:check ... No such reference
```
Cùng commit `a3f7355` chạy local `VERIFY PASS full`.

## Điều kiện tái hiện
`pom.xml` cấu hình Spotless `<ratchetFrom>origin/main</ratchetFrom>`; CI dùng `actions/checkout@v4` mặc định (`fetch-depth: 1`, không có ref `origin/main`).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Chạy lại test local với `-Duser.timezone=UTC` (CI chạy lúc 00:28 giờ VN = 17:28 UTC hôm trước) | 611 pass | Không phải lỗi ngày/múi giờ |
| 2 | Chạy test trong container `eclipse-temurin:21-jdk` (Linux, Testcontainers qua Docker socket) | Không cho thêm thông tin | Container clone bằng `git archive` không có `.git` nên không giống CI; log CI cần đăng nhập GitHub |
| 3 | Đọc log CI (user chụp màn hình) | `spotless:check failed: No such reference` | Lỗi nằm ở ref git, không ở code/test |

## Root cause
Spotless ratchet cần đọc ref `origin/main` để biết file nào đổi; checkout nông trên GitHub Actions không fetch nhánh nào khác ngoài commit đang chạy. Local có đủ remote ref nên không gặp.

## Fix
`.github/workflows/deploy.yml` job `verify`: `actions/checkout@v4` với `fetch-depth: 0` (lấy đủ lịch sử + remote ref).

## Regression test
Không có test tự động; kiểm bằng run Deploy VPS kế tiếp trên `dev` (job `verify` xanh).

## Ảnh hưởng
Mọi workflow mới gọi `spotless:check` hoặc `node scripts/verify.mjs` phải checkout `fetch-depth: 0`.
