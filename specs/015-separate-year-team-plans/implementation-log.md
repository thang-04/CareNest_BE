# Spec015 implementation ledger — 09/11/2026

Plan: specs/015-separate-year-team-plans/plan.md. User explicitly requested implement→converge until clean, comparing project context/business/rules. This supersedes earlier documents saying implementation/API/DDL/client not authorized; it authorizes planned BE/FE code and scratch checks, not Git commit/push or destructive live cutover. Existing checkout is the user working feature branch; preserve all preexisting edits. No project wrappers/hooks.

Checklist requirements.md: 16 checked / 0 unchecked. Phase setup: artifact paths/context reviewed; ignore files already cover Java target, IDE, secrets and Docker output. User context/EDU rules govern intent beyond spec artifacts as explicitly requested.

Coordination: storage owns entities/repos/migrations/migration tests; frontend owns FE; consumers owns class-doc/lesson query paths and focused tests; root owns API/main workflow/sync/diff/integration and task evidence. Reuse current checkout (no worktree/branch/commit).

Ruling: shared EduPlan mapped superclass + physical EduYearPlan/EduTeamPlan and EduPlanRepository data-access routing — keeps existing behavior without duplicating 1800-line service; a common workflow/source method still dispatches by physical entity, explicit team service/updates own new behavior. Child ownership must be initialized explicitly from actual header, no guessed ID scope. Root controls integration of all callers.

Ruling: T009 actual live cutover is not run automatically against an existing DB; candidate SQL and scratch rehearsal proceed, final status must disclose any non-executed live step rather than mark it complete. User must have concrete reviewable candidate/evidence before any live irreversible action.

Open: implementation and fresh native tests, then independent review/convergence. Do not claim 100% runtime from document coverage.
