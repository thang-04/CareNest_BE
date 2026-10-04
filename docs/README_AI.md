# README_AI — Kiến trúc tài liệu cho AI agent (CareNest)

File này dành cho **người** (thành viên team, giảng viên, người mới) muốn hiểu bộ tài liệu dành cho AI coding agent (Claude Code, Codex) đang được tổ chức thế nào: thư mục nào dùng để làm gì, AI đọc theo thứ tự nào, và khi nào cần cập nhật cái gì.

AI agent **không cần** đọc file này. Điểm vào của agent là `AGENTS.md` (Claude nạp qua `CLAUDE.md`).

---

## 1. Ý tưởng chính

| Mục tiêu | Cách làm |
| --- | --- |
| AI hiểu đúng nghiệp vụ, không tự chế rule | Mọi rule có **ID + status + nguồn** trong `docs/business/BUSINESS_RULES.md`. Mục chưa chốt là `PENDING`/`OPEN`, AI phải hỏi |
| AI tìm đúng chỗ để sửa/debug | `.ai/CONTEXT_MAP.yaml` map từ khóa (VN/EN, tên class) → **module card** `docs/modules/<module>.md` |
| AI nhớ lỗi đã gặp | **Engineering memory** trong `docs/knowledge/`: 1 dòng/issue trong `ISSUE_INDEX.md`, chi tiết + các cách đã thử trong `incidents/` |
| Tiết kiệm token | Đọc theo **mức L1→L4** (`.ai/ESCALATION.md`): bug nhỏ chỉ đọc card + mục guide cần thiết; không bao giờ đọc hết `docs/` |
| Một nguồn sự thật | **`CareNest_BE` là nguồn chuẩn** cho nghiệp vụ, API contract, DB. `CareNest_FE` (Web) và `CareNest_APP` (Mobile) chỉ trỏ về BE bằng `BE:<path>`, không chép rule |
| Dùng chung cho mọi agent | Quy trình nằm ở `.ai/` (trung lập công cụ). `.claude/` và `.agents/` chỉ là điểm vào trỏ về `.ai/` |

---

## 2. Ba repo

```text
CareNest_CODE/
├── CareNest_BE/    Spring Boot API — NGUỒN CHUẨN: nghiệp vụ, rule, domain, API contract, DB, memory nghiệp vụ
├── CareNest_FE/    Web (Admin, Hiệu trưởng, Hiệu phó, Giáo viên) — màn hình, route, state, gọi API
└── CareNest_APP/   Mobile (Phụ huynh, Giáo viên, Bếp) — màn hình, navigation, state, push, gọi API
```

Quy ước `BE:<path>` trong FE/APP = đường dẫn tính từ gốc repo BE, ví dụ `BE:docs/modules/attendance.md` = `../CareNest_BE/docs/modules/attendance.md`.

---

## 3. AI đọc theo thứ tự nào

```text
CLAUDE.md ──@import──► AGENTS.md            (nguyên tắc bất biến + quy tắc git/commit/comment chung 3 repo)
                          │
                          ▼
                 .ai/ROUTER.md              loại task → profile + workflow + skill + mục guide + mức L1..L4
                          │
                          ▼
           grep .ai/CONTEXT_MAP.yaml        từ khóa → module card (không đọc cả file)
                          │
                          ▼
           docs/modules/<module>.md         Mục đích · Owns · Rules · Flow · PENDING · Known pitfalls
                          │  chỉ khi cần (theo .ai/ESCALATION.md)
                          ▼
     flow · BUSINESS_RULES (grep theo ID) · ADR · MODULE_MAP · CROSS_REPO_MAP · architecture
                          │
                          ▼
     .ai/workflows/<workflow>.md  →  Checklist guide mục 17 + docs/quality/DEFINITION_OF_DONE.md
                          │
                          ▼
     .ai/workflows/update-knowledge.md  (ghi tri thức mới / bug mới ngay trong lượt)
```

Mức đọc (ước tính cho BE):

| Mức | Khi nào | Đọc thêm | Token |
| --- | --- | --- | --- |
| L1 | Bug/sửa trong 1 file | card + mục guide + dòng ISSUE_INDEX | ~8–10k |
| L2 | Feature trong 1 module | + flow + rule của module + contract/DB doc | ~15–20k |
| L3 | Chạm ≥2 module / FE-APP | + card liên quan + MODULE_MAP + CROSS_MODULE_ISSUES | nhiều hơn |
| L4 / FULL | Kiến trúc, phân quyền, AI layer / audit | toàn bộ phần liên quan | không tối ưu |

---

## 4. Thư mục trong `CareNest_BE` dùng để làm gì

```text
CareNest_BE/
├── README.md                 Cách chạy, cấu hình (biến môi trường), test — cho người và AI
├── AGENTS.md                 Điểm vào của mọi AI agent: cách bắt đầu task, đọc tiết kiệm token,
│                             nguyên tắc bất biến, quy tắc git/commit/comment (đồng bộ 3 repo)
├── CLAUDE.md                 Chỉ `@AGENTS.md` + 2 dòng riêng cho Claude
│
├── .ai/                      QUY TRÌNH CHUNG cho mọi agent (nguồn chuẩn của "làm việc thế nào")
│   ├── ROUTER.md             Bảng chọn: loại task → profile/workflow/skill/mục guide/mức context
│   ├── ESCALATION.md         Mức đọc L1..L4/FULL và ngân sách token
│   ├── CONTEXT_MAP.yaml      keywords → module; tech_keywords → file kỹ thuật; topics; danh sách file
│   │                         hiện có / skeleton / planned. AI grep, không đọc cả file
│   ├── profiles/             "Đọc gì" theo vai: code (L1), feature (L2), architecture (L3-L4),
│   │                         cross-repo, operations, full
│   └── workflows/            "Làm theo bước nào": fix-bug, implement-feature, review-code,
│                             update-api, update-database, deploy, update-knowledge
│
├── .claude/                  Riêng Claude Code
│   ├── settings.json         Đăng ký Stop hook
│   ├── hooks/memory-reminder.mjs   Nhắc cập nhật memory tối đa 1 lần/session khi có sửa code
│   ├── rules/                Coding rule tự áp theo loại file (frontmatter `paths`): java, controller,
│   │                         service, database, security, testing
│   └── skills/<workflow>/    Skill mỏng — chỉ trỏ về `.ai/workflows/`
├── .agents/skills/           Bản mirror y hệt `.claude/skills/` cho agent khác (Codex)
│
├── docs/
│   ├── README_AI.md          File này — giải thích kiến trúc tài liệu cho người
│   ├── INDEX.md              Bản đồ toàn bộ docs + status (FULL / SKELETON) + bảng ADR
│   ├── backend-coding-guide.md   QUY TẮC CODE BẮT BUỘC (17 mục, đọc theo mục router chỉ)
│   ├── context/              Bối cảnh dự án
│   │   ├── PROJECT_CONTEXT.md    Trường, actor, định vị, tính năng V1 (truy vết FE-01..10),
│   │   │                         exclusions, quy ước status, thứ tự ưu tiên nguồn, bảng nguồn bằng chứng
│   │   ├── CURRENT_STATE.md      Đã làm / đang làm / chưa làm; giới hạn hiện tại
│   │   └── GLOSSARY.md           Thuật ngữ tiếng Việt ↔ tên trong code
│   ├── modules/              ĐIỂM ĐỌC CHÍNH: 1 card/module (12 module) — AI đọc card trước tiên
│   ├── business/             Nghiệp vụ
│   │   ├── BUSINESS_RULES.md     Rule có ID (AUTH, ATT, NUT, HLT, OBS, FAC, PAR, AI) + Pending register
│   │   │                         (P-xx) + bảng Đã đóng
│   │   ├── USER_ROLES.md         Role, scope (trường/điểm trường/lớp/trẻ), permission
│   │   ├── DOMAIN_MODEL.md       Entity theo module, quan hệ — nguồn chuẩn tên entity
│   │   └── flows/                Luồng nghiệp vụ: [B] = người làm ngoài hệ thống, [S] = hệ thống làm
│   ├── system/               Toàn hệ thống: SYSTEM_ARCHITECTURE, MODULE_MAP (phụ thuộc giữa module),
│   │                         CROSS_REPO_MAP (BE/FE/APP ai sở hữu gì), DEPLOYMENT (skeleton)
│   ├── architecture/         Thiết kế BE: layered monolith, package, data flow, security
│   ├── contracts/            Hợp đồng API cho FE/APP: API_CONVENTIONS, ERROR_CONTRACT, AUTH (skeleton)
│   ├── database/             Nguyên tắc DB; ERD, DATA_DICTIONARY (skeleton tới khi có migration)
│   ├── decisions/            ADR-0001..0010 — quyết định kiến trúc/scope và lý do
│   ├── knowledge/            ENGINEERING MEMORY (mục 6)
│   ├── quality/              DEFINITION_OF_DONE, TEST_STRATEGY, NFR (có ID)
│   ├── plans/                Kế hoạch thay đổi lớn (active/completed)
│   └── api/                  OpenAPI export (chưa có; runtime Swagger: `/swagger-ui/index.html`)
└── src/                      Source Spring Boot
```

### FE và APP khác BE ở đâu

Cấu trúc `.ai/`, `.claude/`, `.agents/`, `AGENTS.md`, `CLAUDE.md`, `docs/knowledge/`, `docs/quality/` giống BE. Khác:

| Repo | Thư mục riêng | Dùng để |
| --- | --- | --- |
| FE | `docs/architecture/ROUTE_MAP.md`, `FRONTEND_ARCHITECTURE.md`, `STATE_MANAGEMENT.md`, `FOLDER_STRUCTURE.md` | Màn hình/route theo role → permission BE → card BE |
| FE | `docs/features/README.md` | Tính năng web ↔ module/flow BE |
| APP | `docs/features/{parent,teacher,kitchen}/README.md` | Màn hình theo role ↔ flow/rule BE |
| APP | `docs/architecture/NAVIGATION.md`, `MOBILE_ARCHITECTURE.md` | Navigation theo role |
| FE, APP | `docs/integration/` | Cách gọi BE, auth, push (APP) — chỉ hành vi phía client, contract ở BE |
| FE, APP | `docs/context/REPOSITORY_CONTEXT.md`, `CURRENT_STATE.md` | Vai trò repo, trạng thái màn hình |

FE/APP **không** có `business/`, `contracts/`, `database/`, `decisions/` — các thứ đó ở BE.

---

## 5. Trạng thái thông tin và thứ tự ưu tiên

| Status | Nghĩa | AI được làm |
| --- | --- | --- |
| CONFIRMED | Khảo sát/Report 1, chỉ đạo giảng viên đã nhận, hoặc team chốt (có ngày + nguồn) | Dùng làm ràng buộc |
| ACCEPTED | Quyết định kỹ thuật/ADR đã chấp nhận | Dùng làm ràng buộc |
| PROPOSED | Đề xuất (team, roadmap, gợi ý chưa nhận) | Cân nhắc, không coi là requirement |
| PENDING (P-xx) | Chưa khảo sát đủ | Không đoán: làm cấu hình được hoặc hỏi |
| OPEN | Quyết định chưa chốt (có ADR) | Không tự chọn; dừng và hỏi |

Mâu thuẫn nghiệp vụ: **quyết định mới nhất có nguồn > khảo sát/Report 1 > đề xuất > giả định**. Mâu thuẫn code: **source > guide > ADR**.

---

## 6. Engineering memory (`docs/knowledge/`)

| File | Ghi gì | Khi nào |
| --- | --- | --- |
| `ISSUE_INDEX.md` | 1 dòng/issue: ID, module, triệu chứng + chuỗi lỗi, root cause, status (`open`/`workaround`/`fixed`) | Bug không hiển nhiên, thử >1 cách, liên module, lỗi môi trường >15 phút — **kể cả khi chưa fix** |
| `incidents/<ID>.md` | Chi tiết: tái hiện, **Attempts (cách đã thử thất bại)**, root cause, fix, regression test | Cùng lúc với dòng index; cập nhật mỗi phiên điều tra |
| `TROUBLESHOOTING.md` | Lỗi môi trường/config: chuỗi lỗi → nguyên nhân → cách xử lý | Lỗi build/chạy/deploy |
| `CROSS_MODULE_ISSUES.md` (BE) | Rủi ro/issue giữa module hoặc BE↔FE/APP | Bug liên module, liên repo |
| `PATTERNS.md` | Bài học tổng quát (1 dòng + nguồn) | Khi rút ra được quy tắc dùng lại |
| `KNOWN_ISSUES.md` | Giới hạn cố ý / chưa làm (không phải bug) | Khi có giới hạn agent cần biết |

Quy trình ghi duy nhất: `.ai/workflows/update-knowledge.md` (T1 nghiệp vụ mới/chốt PENDING · T2 bug mới · T3 edge case).

---

## 7. Quy ước ID

| Loại | Dạng | Ở đâu |
| --- | --- | --- |
| Business rule | `ATT-05`, `NUT-14`, `OBS-10`... | `docs/business/BUSINESS_RULES.md` |
| Câu hỏi chờ chốt | `P-03` … `P-17` | Pending register (BUSINESS_RULES) |
| Quyết định | `ADR-0001` … | `docs/decisions/` |
| NFR | `NFR-PERF-01`... | `docs/quality/NFR.md` |
| Issue BE | `BUG-YYMMDD-slug`, `CASE-…`, `ENV-…` | BE `docs/knowledge/` |
| Issue FE / APP | `FE-BUG-YYMMDD-slug` / `APP-BUG-YYMMDD-slug` (+ `CASE`, `ENV`, `CTR`) | `docs/knowledge/` từng repo |
| Pattern | `PAT-` (BE), `FP-` (FE), `AP-` (APP) | `PATTERNS.md` |
| Rủi ro liên module | `CMR-01`... | BE `CROSS_MODULE_ISSUES.md` |

Trong code: comment/test name dẫn rule ID khi implement rule (vd. `// NUT-03: không sửa số suất đã xác nhận`).

---

## 8. Việc của thành viên team

| Tình huống | Làm gì |
| --- | --- |
| Giao task cho AI | Nói rõ task; AI tự đi theo router. Bug: dán chuỗi lỗi nguyên văn |
| Trường/giảng viên chốt một điều | Nói với AI "trường chốt …" → AI chạy update-knowledge T1 (sửa rule, đóng P-xx, grep mọi chỗ tham chiếu) |
| Thêm module mới | Tạo card theo template trong `docs/modules/README.md`, thêm keywords vào `CONTEXT_MAP.yaml`, cập nhật `MODULE_MAP.md` |
| Sửa skill | Sửa `.claude/skills/` và chép y hệt sang `.agents/skills/` |
| Sửa khối "Quy tắc chung CareNest" trong `AGENTS.md` | Đồng bộ sang 2 repo còn lại (chỉ "Hỏi trước khi làm" và "Phạm vi" khác nhau) |
| Đổi API/auth | Workflow `update-api` + cập nhật `CROSS_REPO_MAP.md`; báo FE/APP |
| Review AI output | Kiểm tra AI dẫn rule ID, không implement rule PENDING/OPEN như đã chốt, có cập nhật memory |

Yêu cầu máy: Hook Claude cần Node ≥ 18. Không commit `.env`, `.claude/settings.local.json`.
