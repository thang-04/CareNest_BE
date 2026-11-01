---
id: BUG-261012-keycloak-email-immutable-attributes
type: bug
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-12
keywords: [Keycloak, SMTP, FreeMarker, EmailException, UnsupportedOperationException, Map.of]
similar_to: []
---
# Provider email fails when template attributes are immutable

## Symptom
Real-container auth E2E passes 12 security scenarios, then OTP email returns 503 `Email delivery is unavailable`.

## Điều kiện tái hiện
Call the authenticated extension recovery/email operation with configured Mailpit and a valid fake account.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Pass Map.of directly to Keycloak EmailTemplateProvider.send | Real email fails; mocked unit test passes | The real provider enriches the supplied attributes map |
| 2 | Regression mock adds locale like the real provider; copy attributes into mutable HashMap | RED 1 failure/0 errors; GREEN; real OTP/setup emails delivered to Mailpit | Preserve provider contract without logging body/OTP |

## Root cause
Keycloak 26.8.0 FreeMarkerEmailTemplateProvider.processTemplate adds locale, msg and other entries to its input map. AuthOperations passed Map.of, which rejects mutation. Source checked in the version-pinned official provider, lines 209–233.

## Fix
Pass a mutable copy to the email provider. No extra runtime dependency or SMTP change.

## Regression test
`AuthOperationsTest.emailProviderCanEnrichTemplateAttributes`; RED 1 failure/0 errors → GREEN. Extension suite currently 24 tests/0 skip. Real-container E2E proves both OTP and React setup email delivery.

## Ảnh hưởng
New extension email operations only; existing backend provisioning unchanged.

## Lesson
Mocks of provider boundaries must preserve observable provider behavior; a successful mocked send does not establish real template/SMTP compatibility.
