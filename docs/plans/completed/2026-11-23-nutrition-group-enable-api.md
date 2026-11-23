---
title: Persist nutrition group ON/OFF in API mode
status: done
owner: Nguyễn Duy Thắng
jira: G94-447 / BE-FEAT-01
branch: fix/G94-447-BE-FIX-01-nutrition-group-status
modules: [nutrition, menu-planning]
rules: [NUT-17]
risk: high
created: 2026-11-23
updated: 2026-11-23
---

# Reuse nutrition group ON/OFF in API mode

## Context

- FE already implements `g.enabled` and persists it in mock mode: `CareNest_FE/src/pages/menu-planning/NutritionSetupPage.jsx:221` and `CareNest_FE/src/services/menu-planning/mock/menuPlanningMockRepository.js:1031`.
- API mode removes the existing `groups` tab at `CareNest_FE/src/pages/menu-planning/NutritionSetupPage.jsx:97`; `NutritionSetupGroup` also hides its existing switch for `usesApi`.
- API setup responses hardcode `enabled: true` and the API save mapper omits it: `CareNest_FE/src/services/nutritionCatalogApi.js:583` and `:345`.
- BE has no persisted nutrition-enabled state in `age_groups` or `age_group_meal_configs`; NUT-17 currently covers principal-owned, effective-dated meal/nutrition configuration.

## Quyết định đã chốt

1. Reuse the existing `enabled` behavior and single `g.enabled` state; do not create a second toggle flow.
2. OFF excludes the age group from new menu composition only. It does not deactivate the school age group, classes, attendance, or historical menus. Effective dates continue to choose nutrition-config versions; they do not control ON/OFF.
3. Persist the flag as a nutrition-owned setting separate from effective-dated config versions. Existing age groups default to ON. Only `MEAL_CONFIG_MANAGE` (Principal) may change it.
4. Keep the existing group tab behavior, and surface the same toggle in the `Định mức dinh dưỡng` detail and an ON/OFF action in `Tổng quan`. All three edit the same form state and are committed through the existing Save/confirm flow.

## Làm rõ nghiệp vụ

- Actor: Principal with `MEAL_CONFIG_MANAGE`.
- Outcome: ON/OFF is editable in “Nhóm độ tuổi”, the “Định mức dinh dưỡng” detail, and “Tổng quan”; OFF removes that group from new menu planning.
- Rule: NUT-17 (CONFIRMED for principal ownership/effective-dated norms); ON/OFF behavior is PROPOSED from user instruction, 2026-11-23.
- Scope: nutrition/menu planning only; school age-group and class records remain active.

## Acceptance criteria

- AC-1: Given existing age groups without a stored nutrition setting, when API setup loads, then each defaults to enabled and existing menus/configs remain unchanged.
- AC-2: Given a Principal turns a group OFF in any of the three surfaces and saves, when setup reloads, then every surface shows OFF and the group is excluded from new menu choices.
- AC-3: Given a group is OFF, when historical menus are read or evaluated, then their age-group data remains available and unchanged.
- AC-4: Given a non-Principal caller or a caller without `MEAL_CONFIG_MANAGE`, when status changes are submitted, then BE rejects the change.
- AC-5: Given the API is unavailable or the save fails, when the user edits ON/OFF, then FE does not report the change as saved.

## Key decisions

| Decision | Choice | Alternative | Reason |
| --- | --- | --- | --- |
| State storage | Nutrition-specific per-age-group setting, independent of versioned configs | Add `enabled` to every dated config row | Toggle is immediate and must not change `effectiveFrom` or duplicate state across versions |
| Existing data | Default missing settings to ON | Default OFF | Preserves current behavior for all existing groups |

## Phases

### Phase 1 — BE persistence and menu enforcement

- Files: migration, nutrition setting entity/repository/DTO/service/controller, menu write validation, API docs, focused BE tests.
- Steps: add per-group state defaulting ON; expose status in nutrition setup reads; accept status changes in the same transactional save; enforce OFF for new menu composition; preserve historical reads.
- Tests: controller authorization; persistence/default behavior; menu-create rejection when OFF; historical read remains available.
- Exit: focused BE tests and migration checks pass.

### Phase 2 — Reuse existing switch across FE surfaces

- Files: `NutritionSetupPage.jsx`, `NutritionSetupGroup.jsx`, `nutritionCatalogApi.js`, focused FE tests.
- Steps: initialize `enabled` from BE; show the same switch in the existing group tab, the nutrition detail, and an Overview row action; submit changed flags with config edits; retain current mock behavior.
- Tests: API adapter maps/saves status; each surface updates the shared form state; OFF filters new menu options; failed save preserves unsaved state.
- Exit: focused FE tests pass; verify diff contains no unrelated work.

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Existing clients omit new settings | Groups could disappear | Migration defaults every existing group to ON; API defaults missing values to ON | Keep default ON and revert only the new write path |
| Status accidentally affects school structure or historical menus | Existing class/attendance/report data could be hidden | Scope checks only menu creation/selection; do not filter shared age-group or historical read endpoints | Disable enforcement and retain stored state |

## Validation log

- [x] Current FE mock/API behavior and BE age-group/config schema inspected.
- [x] Scope chosen from user response: menu planning only; preserve age groups/classes.
- [x] User authorized implementation: "vá đi cho tôi", 2026-11-23.
- [x] Controller/service tests and PostgreSQL NutritionCatalogIntegrationTest cover status persistence, default ON and authorization.
- [ ] Current unrelated FE dirty files remain untouched.

## Progress log

### 2026-11-23 — Plan draft

- Commit: not committed.
- Verify: source inspection only; no tests run.
- Next: user review and plan approval.

### 2026-11-23 — Implementation
- BE: V170 + GET statuses + atomic batch status/config save; menu planning/AI filters disabled groups, published/history retains original group reads.
- FE: existing tab/switch shown with API, Overview action, shared enabled state; status-only save omits effective-date input.
- Verification: initial 45 BE tests passed, 24 FE nutrition setup/adapter tests passed; extended BE regression run: 72 tests passed, 0 failed/errors/skipped (service, controller, PostgreSQL, meal plans, amendments, AI drafts, OpenAPI).
- Not committed or deployed.

- FE: 24 nutrition adapter/setup tests pass; scoped ESLint and Prettier pass. Production build blocked locally by missing `mammoth` in announcement template imports. Browser/live dev verification not performed.
