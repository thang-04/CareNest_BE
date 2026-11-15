---
id: BUG-261107-edu-required-future-endpoint
type: bug
modules: [education-plan]
status: fixed
date: 2026-11-07
keywords: [duration-policy, endpoint, FE]
---

# FE education pages fail on an unimplemented duration endpoint

- Date: 2026-11-07. Source: user reports “không tìm thấy dữ liệu” on education pages after feature012 FE review.
- Root cause: the active FE uses real API, while the FE-first change added GET `/education-plans/duration-policy` to the provider's required Promise.all before BE implemented it. HTTP404 becomes the module-wide ErrorState. The old goals API may also ignore planLevel and return TEAM twice.
- Reproduction: Axios adapter returns valid config/workflow and404 only for duration-policy; getEduConfig rejected. Regression failed with Not found, then passed after the fix. No browser opened and no real session/token accessed.
- Fix in CareNest_FE: tolerate404 only on the new optional duration read, record durationPolicyAvailable=false, disable unavailable duration/annual controls, preserve legacy TEAM creation/branch v1 writes/policy3keys, preserve unavailable capability after config saves. Dedupe legacy list IDs and never relabel old TEAM results as annual. No synthetic data fallback. 403/500 still propagate.
- Validation: direct Node education+menu409/409 pass,0fail/0skip; ESLint changed source exit0; Vite build exit0. API/DOM with the user's session not independently exercised.
- Prevention: future endpoints must not become hard prerequisites of already deployed screens during FE-first reviews. Test against the previous backend contract, including unsupported endpoint404 and config save/refresh.
- Status: fixed in FE code; runtime confirmation by user pending. Backend feature implementation remains deferred until FE approval.

## Follow-up: PHT cannot see the annual create button

- User confirmed annual plans are created by PHT, but create button absent. Cause: the404 compatibility patch tied canCreate/canEdit to durationPolicyAvailable and the policy fallback used TEAM creators for annual.
- Fix: annual defaults PHT→HT independently of duration API availability; explicit annual creation scopes from server stay authoritative. Legacy TEAM creation scopes do not control annual. PHT can open/edit the FE review form; save/submit remain disabled with an explicit preview notice while the new API is unavailable. No mock data substituted and no server permission bypass.
- Regression: SSR test reproduced absent button, then GREEN for PHT create link, non-creator no-link, explicit empty server scopes no-link and editable preview form. Direct education tests231/231, ESLint two source files exit0, Vite build exit0. No browser opened or backend source changed.
