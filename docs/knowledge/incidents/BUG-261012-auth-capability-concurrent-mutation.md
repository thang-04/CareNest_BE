---
id: BUG-261012-auth-capability-concurrent-mutation
type: bug
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-12
keywords: [Keycloak, capability, race, generation, transaction completion, SingleUseObjectProvider]
similar_to: []
---
# Serialize capability issuance and credential mutation per user

## Symptom
Review identified an interleave where a new capability could be issued while a previous capability was replacing the credential. Separate one-use token removal did not serialize different tokens for the same user.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Regression tries issuing B inside A's updateCredential | Expected conflict but no exception (RED) | Sequential replay coverage missed a generation race |
| 2 | Shared realm/user cache lock around issuance and mutation, held until Keycloak transaction completion | 24 extension tests pass | Keep the lock through commit/rollback, not merely through method return |

## Root cause
validateUser and consume were independent; issuing another generation could overlap credential update before its commit.

## Fix
Use atomic SingleUseObjectProvider.putIfAbsent per realm/user; a bounded lease blocks concurrent operations. Release via enlisted transaction completion on commit or rollback. Capability expiry/credential fingerprint and atomic one-use removal remain in force. Keycloak transaction timeout must remain below the 600-second lease.

## Regression test
`AuthOperationsTest.cannotIssueAnotherCapabilityDuringCredentialMutation` RED→GREEN; `capabilityLockIsReleasedOnlyAfterKeycloakCommitOrRollback` proves transaction-bound release.

## Ảnh hưởng
New provider only; no existing user credentials were changed. Test accounts are generated and disposable.

## Lesson
A one-use token is not a per-user concurrency guard when multiple tokens may be issued.
