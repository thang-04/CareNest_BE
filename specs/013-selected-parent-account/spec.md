# Specification — 013
User approved implementation 2026-11-05. Domain AC-1..8 and the selected-parent-account contract are normative.

## Stories and requirements
FR1 choose exactly one guardian for account operations; singleton auto-selects, multiple require explicit selection. FR2 form creates/links only the recipient on save, preserving permission exceptions. FR3 CSV/XLSX/Excel preview stores full guardians and selected recipient, import performs no account side effects, activation occurs later. FR4 account list/panel/coverage show only recipient. FR5 retain other contacts/photos/pickup. FR6 enforce server-side eligibility, scope, stale selection and idempotency across legacy account routes too. Old links are never silently revoked.

## Acceptance
AC1 selection and identity after reorder/removal; AC2 one form side effect only; AC3 legacy one-guardian and multi-guardian spreadsheet validation; AC4 deferred import and selected-only activation; AC5 shared existing accounts; AC6 direct API, scope and stale modal; AC7 photo/pickup and rollback; AC8 safe legacy selection migration. Full BE/FE gates and desktop/mobile flows verify these scenarios.

## Clarifications
Form immediate, Excel deferred, one recipient each. All design decisions in the presented plan authorized by the implementation request. No password/SMS policy change, APP work, new dependencies, forced account closure or automatic revocation of old links.
