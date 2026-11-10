---
title: Harden plan uploads and class lock ordering
status: in-progress
owner: DinhCan
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [education-plan, observation, attendance]
rules: [EDU-46, ATT-05]
risk: high
created: 2026-11-02
updated: 2026-11-02
---
# Technical review repairs

User approved the proposed five technical repairs and PR creation in the latest request. This is bug repair, not a new feature. Keep Bé ngoan/Bé ngoan toàn diện rules and specialist attendance denominator unchanged. Broader history/scope audit findings remain separate and open.

## Acceptance criteria and decisions

- AC1: malformed HTML tables fail with a controlled existing error within bounded work; avoid wildcard body regex and repeated string concatenation. Preserve Word HTML merged cells and valid existing fixtures; use bounded forward tag scanning without a new dependency.
- AC2: every ZIP entry, including skipped entries, is read within an entry, archive and entry-count budget. Limits protect memory before XML DOM allocation; upload remains 2 MB. Use 8 MB per entry, 32 MB cumulative archive and 256 entries, with tests on legitimate and hostile fixtures.
- AC3: assessment requests lock multiple classes in ascending ID order, matching leave requests. Test ordering and independent concurrent transactions involving both services.
- AC4 (FE): both import dialogs reject empty, unsupported and over-2-MB files before network calls; valid suffixes unchanged (.doc/.docx/.xlsx/.csv), case-insensitive. Backend remains authoritative.
- AC5 (FE): retire remaining condition examples/imports from /ui-kit and smoke-test the dev route.

## Phases and validation

1. Parser/resource regression tests, implementation and targeted tests.
2. Lock ordering and FE validation/dev-route repairs; focused tests.
3. Full `node scripts/verify.mjs` both repos, AI-layer checks, peer review required by review-code workflow, push current task branches and create new PRs targeting dev. Source base fast-forwarded from origin/dev; preserve local application.yml, SQL and prior audit documents outside commits.

## Progress log

- Approved by user request to implement and create PR; preserving previous decisions on reward/attendance formulas. No API/dependency/migration changes planned. Existing task branches updated to current dev before edits.

- Implementation verified: 24 targeted tests passed, including concurrent PostgreSQL leave/evaluation; full verify PASS with 737 tests, 0 failures/errors/skips, ArchUnit/OpenAPI snapshot/Spotless passed. FE full verify PASS with 490 tests and Chrome dev UI kit smoke. Read-only peer review found no introduced blocker. No API, migration, dependency or reward/attendance formula changes. Awaiting pull-request review; unrelated audit/config files remain outside commits.
