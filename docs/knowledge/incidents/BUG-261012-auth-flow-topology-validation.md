---
id: BUG-261012-auth-flow-topology-validation
type: bug
modules: [identity-access]
rules: [AUTH-07]
status: fixed
date: 2026-10-12
keywords: [Keycloak, direct grant, bootstrap, topology, disabled parent, password bypass]
similar_to: []
---
# Bootstrap must validate authentication flow ancestry

## Symptom
Independent review found that REQUIRED password/OTP executions nested inside a DISABLED subflow passed the initial validator. Existence and order alone do not prove the executions will run.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Add disabled-parent regression against initial validator | Missing expected exception (RED) | Child requirement alone does not establish effective execution |
| 2 | Require exact Keycloak 26.8.0 native topology plus setup checkpoint | All 8 Node tests pass | Reject disabled/alternative parents, nested password, extra OTP conditions and duplicate checkpoints |

## Root cause
The initial check enforced top-level only for the checkpoint, not for credential validators or OTP ancestry.

## Fix
Require top-level username/password REQUIRED; one CONDITIONAL OTP subflow with user-configured and OTP children REQUIRED; then exactly one top-level REQUIRED checkpoint. A failed update leaves the private client disabled.

## Regression test
`bootstrap.test.mjs`: disabled parent, disabled/alternative OTP parent, extra condition and nested checkpoints. The main disabled-parent test failed before fix and passes after.

## Ảnh hưởng
New opt-in bootstrap only; no existing realm was updated. Runtime negative topology scenario is included in isolated E2E.

## Lesson
Authentication execution topology is a security boundary; a child marked REQUIRED may still be skipped by its ancestor.
