# Implementation plan — 013
Reuse Java 21/Spring/PostgreSQL/Flyway and React JS/Vite. Existing POI supports XLS; XLSX uses bounded ZIP/XML parsing with secure XML settings, CSV uses quoted-field parser; no new dependency.

1. Add nullable-transition-compatible recipient flag and partial unique constraint; backfill only unambiguous guardians. Persist selection in child service and validate before identities; select-only account orchestration.
2. Add scoped recipient read/select/pending/activate APIs and import preview/import with no account effects. Enforce recipient eligibility at legacy add route; keep full contacts/photos and existing links.
3. FE recipient radio and persisted flag, multi-guardian import parser/template/preview dropdown, selected-only account projection/panel/counters and change selection for legacy records. API/mock parity.
4. Targeted test/HTTP/PostgreSQL, intentional snapshot update, browser 1440/375, full verification, independent review and converge.

One phase, review at end. Existing source selection remains demo for incomplete module integrations; no silent promotion of unrelated APIs. Preserve prior committed image work and local artifacts.
