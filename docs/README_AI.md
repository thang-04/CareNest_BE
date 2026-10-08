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
| Tiết kiệm token | **Làn S/M/L** (`AGENTS.md`, mục 9): task nhỏ chỉ đọc file đích + test; đọc theo **mức L1→L4** (`.ai/ESCALATION.md`); không bao giờ đọc hết `docs/` |
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
│                             update-api, update-database, deploy, update-knowledge,
│                             clarify-business (cổng nghiệp vụ), plan-change (plan làn L)
│
├── .claude/                  Riêng Claude Code
│   ├── settings.json         Đăng ký hook + tắt AI attribution
│   ├── hooks/                session-orient (đầu phiên), guard-edits (chặn/hỏi), track-activity,
│   │                         stop-gate (verify theo làn, memory, AI-layer); harness.config.json
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
│   ├── quality/              DEFINITION_OF_DONE (theo làn), VERIFICATION (bằng chứng), TEST_STRATEGY, NFR
│   ├── plans/                Plan làn L: _TEMPLATE.md, active/, completed/
│   └── api/openapi.yaml      Snapshot hợp đồng API (test khóa; runtime Swagger: `/swagger-ui/index.html`)
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
| Task nghiệp vụ AI hỏi lại nhiều vòng | Trả lời rõ, nói mức chốt (trường/team chốt hay chỉ đề xuất) — AI ghi vào `BUSINESS_RULES.md` để không hỏi lại |

---

## 9. Quy trình & cổng kiểm soát (ADR-0012)

Mục tiêu: task lớn làm đủ quy trình, task nhỏ không tốn token vô ích; kiểm tra nặng do máy làm (hook, script, Maven), không dùng token model.

### Làn S/M/L (chi tiết: bảng trong `AGENTS.md`)

```text
S  ≤2 file, việc rõ        → file đích + test → sửa → verify --quick → báo ≤3 dòng
M  3–8 file, 1 module      → router → card → [clarify-business] → mini-plan → code + test → verify → báo ≤8 dòng
L  vùng rủi ro / ≥2 module → clarify-business (hỏi tới khi rõ) → plan draft → USER DUYỆT
                             → mỗi phase: code → verify → Progress log → DỪNG review
                             → review (spec → severity → verdict) → DoD → done, chuyển completed/
Mọi làn: tri thức/bug mới ⇒ update-knowledge · commit chỉ khi user cho phép
```

Vùng rủi ro = migration, `security/`, `integration/`, API contract, dependency/hạ tầng. Hook tính làn thực tế từ diff — agent không hạ làn để né quy trình được.

### Cổng làm rõ nghiệp vụ
Task đổi hành vi nghiệp vụ mà rule chưa có / PENDING / OPEN / yêu cầu lệch tài liệu ⇒ agent đưa block "Hiểu nghiệp vụ" (actor, outcome, rule + status, ảnh hưởng, chỗ lệch tài liệu `file:line`) rồi hỏi theo vòng (≤5 câu, có phương án + đề xuất) tới khi rõ. Câu trả lời được ghi vào `BUSINESS_RULES.md` (T1) nên lần sau không hỏi lại. Nguồn: `.ai/workflows/clarify-business.md`.

### Các lớp kiểm soát

| Lớp | Chạy khi | Làm gì |
| --- | --- | --- |
| Chữ | Mọi agent (Claude, Codex) | `AGENTS.md`, `.ai/workflows/`, `docs/quality/VERIFICATION.md`, DoD |
| Claude hooks | Mở Claude **bên trong** repo (không phải thư mục `CareNest_CODE/`) | Đầu phiên: plan đang làm, branch thiếu commit AI-layer của main, git hook chưa bật. Khi sửa: chặn đọc/ghi `.env`; hỏi xác nhận khi sửa migration đã commit, `pom.xml`/Docker, `ResponseJson`/`ApiCode`, vùng rủi ro chưa có plan duyệt; hỏi trước commit/push/tạo branch; cấm `--no-verify`. Khi dừng: chưa có verify sau lần sửa cuối; gợi ý ghi memory khi có dấu hiệu bug khó |
| Git hooks | Mọi người sau khi bật | Commit message (`[<mã-công-việc>] <mã-jira>: <mô tả>`, cấm AI attribution; nhánh sai định dạng hoặc mã lệch nhánh chỉ cảnh báo); chặn `.env`, secret, sửa migration cũ; chạy check-ai-layer; pre-push kiểm lại message, chặn force push |
| Kiểm chứng | Chạy tay hoặc hook gọi | `node scripts/verify.mjs [--quick]` (tóm tắt ≤10 dòng, log đầy đủ ghi ra file), `node scripts/check-ai-layer.mjs` |

### Thiết lập 1 lần mỗi máy
- Node ≥ 18 trên PATH; Docker chạy (Testcontainers).
- Git hook tự bật (`core.hooksPath=.githooks`) khi mở Claude trong repo hoặc chạy `node scripts/verify.mjs`; bật tay: `git config core.hooksPath .githooks`.
- Không có CI: nên bật `git config carenest.verifyOnPush true` để chạy verify trước khi push.

### Khi Claude hỏi xác nhận
- "Chưa có plan approved" ⇒ duyệt plan, yêu cầu lập plan, hoặc nói rõ vì sao không cần.
- "Migration đã commit" ⇒ chỉ đồng ý khi migration chưa chạy ở môi trường nào; còn lại tạo migration mới.
- Commit/push/tạo branch ⇒ xác nhận đúng ý mình (khớp quy tắc "không commit khi chưa cho phép").
- Hook lỗi chặn nhầm ⇒ mở Claude với biến môi trường `CARENEST_HARNESS=off`, rồi báo lại để sửa hook.

### Đọc kết quả cổng
- `VERIFY FAIL` ⇒ đọc các dòng lỗi được tóm tắt; cần chi tiết thì grep file log mà dòng VERIFY chỉ tới. Snapshot fail = API đã đổi (cố ý ⇒ cập nhật snapshot theo `update-api.md`). Spotless fail ⇒ `mvnw.cmd spotless:apply`.
- "Chưa kiểm chứng" ≠ fail: thiếu bằng chứng (vd. Docker tắt nên test tích hợp bị skip).

Yêu cầu máy: Hook Claude cần Node ≥ 18. Không commit `.env`, `.claude/settings.local.json`.
