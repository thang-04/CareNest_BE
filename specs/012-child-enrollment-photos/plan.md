# Implementation plan — 012
Java 21/Spring Boot/PostgreSQL/Flyway and React JS/Vite, existing private S3 and axiosClient; no dependency change.

1. Existing intake scope/form checks precede multipart validation. Validate every image before Keycloak/SMS. Store images inside existing account lifecycle DB callback; remove new objects on rollback. Keep compensation/post-commit SMS.
2. Nullable photo metadata on Child/ChildGuardian, stable guardian id read routes. Enrich intake view/summary and pickup; preserve account routes.
3. FE File state follows each guardian; multipart only when files exist. Preview and private blob object URLs are revoked. Mock compresses demo photos before persistence. Server errors map to step 1.
4. Meaningful regression tests, intended OpenAPI delta, full gates, browser checks, review and converge.

Governance plan: docs/plans/active/2026-11-05-child-enrollment-photos.md. Source finding: FE already reads /children/{id}/profile but BE lacks that route. Add an authorized adapter to the existing intake view, preserving GET /children/{id}. BE has no PUT child-profile endpoint; do not invent editing functionality. Verify metadata survives current lifecycle operations and FE mock edits.
