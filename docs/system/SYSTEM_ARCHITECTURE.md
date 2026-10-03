# System Architecture

## Tổng thể

```text
 Admin / BGH / GV ──► CareNest_FE (Web) ─┐
 PH / GV / Bếp ─────► CareNest_APP (Mobile)┼─ HTTPS/JSON ─► CareNest_BE (Spring Boot, 1 deployable)
                                          │                    ├─ PostgreSQL (1 database)
                                          │                    ├─ File storage (ảnh sự cố — PENDING)
                                          │                    ├─ Push provider (PENDING)
                                          │                    └─ AI provider qua adapter (OPEN, ADR-0007)
 PMS / GoKids / Zalo / hệ thống ngành: hệ thống ngoài, CareNest KHÔNG thay thế; tích hợp GoKids: ADR-0006
```

- BE: layered monolith, ranh giới theo feature (ADR-0001, `docs/backend-coding-guide.md`). Module: `docs/system/MODULE_MAP.md`.
- Client không chứa business rule; mọi rule + authorization ở BE.
- Quy mô ~506 trẻ, ~60 nhân sự: 1 instance BE + 1 PostgreSQL đủ dùng. Không cần microservice, message broker, cache phân tán.

## Luồng nghiệp vụ chính

| Flow | File |
| --- | --- |
| Điểm danh → báo ăn → suất đã chốt → bếp | `docs/business/flows/attendance.md` |
| Đơn nghỉ → điểm danh → suất | `docs/business/flows/leave-request.md` |
| Thực đơn → định lượng → bếp | `docs/business/flows/meal-management.md` |
| Sức khỏe định kỳ | `docs/business/flows/health-check.md` |
| Quan sát → hồ sơ phát triển → summary | `docs/business/flows/child-observation.md` |
| Sự cố CSVC | `docs/business/flows/facility-issue.md` |

## Access scope (ADR-0002, ADR-0003)

```text
StaffAssignment + GuardianChildLink → AccessScopeService(user, date) → {SCHOOL | CAMPUS[] | CLASS[] | CHILD[]}
→ service kiểm tra/lọc dữ liệu theo scope → reporting tổng hợp trong scope
```

Shared DB, dữ liệu campus-scoped mang `campus_id`. Không schema-per-campus, không tenant.

## AI layer (ADR-0007, ADR-0008)

```text
Authoritative data → Deterministic rules → AI assistance (DRAFT) → Human review → Official result
```

- Áp dụng: gợi ý thực đơn, diễn giải trend sức khỏe, nháp nhận xét.
- LLM không bao giờ ghi trạng thái chính thức. AI tắt ⇒ baseline không AI vẫn chạy.

## Dữ liệu dẫn xuất (ADR-0005)

Số suất đã xác nhận không bị sửa ngầm; thay đổi sau đó ⇒ lịch sử + xác nhận lại; bếp chỉ thấy số đã xác nhận. DevelopmentProfile là read model query trực tiếp, không lưu bản sao.

## Ngoài phạm vi kiến trúc V1

24/7 HA, DR đầy đủ, multi-tenant, message broker, tích hợp CSDL quốc gia.
