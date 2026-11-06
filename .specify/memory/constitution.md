# CareNest constitution
Version 1.0.0 — 2026-11-05. Restates existing AGENTS.md principles for Spec Kit; does not replace repository instructions.

1. Preserve existing Java 21/Spring Boot/PostgreSQL and feature-layer architecture; dependencies need authorization.
2. BE owns rules, API and server enforcement of campus/class/child scope.
3. No real personal data/secrets in tests/logs. Private child media is not sent to AI.
4. Reuse integration/storage services; no public child-photo storage.
5. Preserve approved consumer compatibility; schema changes use new Flyway migrations.
6. Domain spec → clarify/plan/model/tasks/analyze → implement/converge; plan L requires user approval.
7. Verification evidence is real; disclose failures/skips. No unapproved commit/push.
