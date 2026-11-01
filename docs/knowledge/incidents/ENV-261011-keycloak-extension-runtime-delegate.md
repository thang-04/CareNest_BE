---
id: ENV-261011-keycloak-extension-runtime-delegate
type: env
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-11
keywords: [Keycloak, extension, JAX-RS, RuntimeDelegate, Maven]
similar_to: []
---
# Standalone extension tests lack JAX-RS RuntimeDelegate

## Symptom
`Provider for jakarta.ws.rs.ext.RuntimeDelegate cannot be found` when the unit test constructs a JAX-RS Response; 5 tests, 1 error.

## Điều kiện tái hiện
Run the standalone extension Maven tests outside the Keycloak/Quarkus server.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Keycloak SPI/services provided dependencies only | Response.status fails in unit tests | APIs are present but the server-provided RuntimeDelegate implementation is not |
| 2 | Add test-only resteasy-core 6.2.19.Final, matching Keycloak 26.8.0 parent | 21 tests pass, 0 skip | Do not bundle a second server REST implementation in the extension JAR |

## Root cause
Standalone unit JVM lacks the JAX-RS implementation supplied by Keycloak at runtime. Keycloak 26.8.0 parent pins RESTEasy 6.2.19.Final; services declares it provided.

## Fix
Add that exact RESTEasy version in test scope of the standalone extension only. No backend/runtime dependency change.

## Regression test
`SetupRequiredAuthenticatorTest.temporaryPasswordProducesOnlyASetupCapability` failed before fix; extension Maven verify passes 21 tests, 0 skip after fix.

## Ảnh hưởng
Extension unit tests only; no user data or API mutation.

## Lesson
Provider-module unit tests need an actual JAX-RS test runtime; keep it out of the deployable provider JAR.
