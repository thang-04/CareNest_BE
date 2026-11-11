# Specification — 012
User approved 2026-11-05 ("duyệt"). Domain spec AC-1..8 are normative.

## User stories and functional requirements
FR-1 Staff with CHILD_ENROLL selects an optional child portrait and one optional portrait per guardian, previews/replaces/removes pending files, and reviews them at confirmation.
FR-2 Navigation and validation failure retain files for the correct people; unrelated form edits retain stored photos.
FR-3 Backend validates all photos before identity/SMS side effects, stores private photos with intake metadata, and scopes reads.
FR-4 Pickup prefers the per-child guardian photo, including unactivated guardians, before existing account-photo fallback.
FR-5 Existing JSON enrollment, account-photo endpoints and records without portraits remain compatible.

## Clarifications
Approved: optional photos; JPEG/PNG/WEBP ≤2 MiB each; per-child guardian photos distinct from account photos; one BE+FE phase then review. No unresolved business decision. No camera/recognition/public links/APP changes.

## Success criteria
Targeted regressions, full gates and desktop/mobile browser evidence cover AC-1..8. Results must disclose missing infrastructure and unrelated failures. All fixtures are synthetic.
