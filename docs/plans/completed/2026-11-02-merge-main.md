---
title: Merge main into attendance branches
status: done
owner: DinhCan
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [attendance, learning-observation, school-structure]
rules: [ATT-01, HLT-07]
risk: high
created: 2026-11-02
updated: 2026-11-02
---
# Merge main into the attendance branch

## Scope

User requested merging origin/main into the current BE and FE branches and updating PRs into dev. Preserve local configuration and SQL fixtures outside commits.

## Decisions

- Keep released main migrations V41/V42; renumber branch migrations V41–V49 to V43–V51.
- Keep released error codes 2320/2321 and 4018; assign vice-principal errors 2322/2323 and LEAVE_NOT_GUARDIAN 4026.
- Preserve assessment, semester setup, parent leave and recent reception UI while incorporating main permission, substitute-teacher, meal and idempotency changes.
- Existing databases with the branch's old migration numbers require a separately reviewed migration strategy; no user database reset is performed.

## Progress log

- Resolved text conflicts, combined test imports, preserved both sets of features and kept released identifiers.
- Initial Maven invocation needed quoted PowerShell -D arguments. Docker is unavailable; integration and snapshot tests skip, so snapshot regeneration remains unverified.
- FE contract routing was updated to retain the branch's vice-principal API and staff directory integration; corrected a formatting conflict.

- Docker became available. Clean rebuild removed stale compiled migrations; regenerated OpenAPI snapshot passed all 3 tests without skips. Full verification is running.
- FE full verification and API contract gate passed after resolving routing and formatting conflicts.

- Final BE full verification PASS: 693 tests, 0 failures, 0 errors, 0 skips; ArchUnit, OpenAPI snapshot and Spotless passed. FE full verification PASS.
