# Prompt mẫu: Spec Kit và harness CareNest

Dùng các mẫu dưới đây để yêu cầu một task theo đúng quy trình Spec Kit và chọn harness sẵn có vừa đủ. Trước khi dùng, điền mô tả, mã Jira nếu có, module và repo cần sửa. `AGENTS.md`, workflow/module guide hiện hành và yêu cầu mới nhất của user luôn có hiệu lực cao hơn mẫu.

## Chọn quy trình và harness

| Loại task | Spec Kit / workflow | Harness tối thiểu phù hợp |
| --- | --- | --- |
| Bug nhỏ, nguyên nhân rõ, ≤2 file cùng module | `fix-bug`; không tạo feature spec nếu không đổi nghiệp vụ | Test hồi quy liên quan + `node scripts/verify.mjs --quick` |
| Bug nhiều file, hành vi sai trong một module | `fix-bug`, tra `ISSUE_INDEX.md`, ghi root cause/attempts | Test module liên quan + `node scripts/verify.mjs` |
| Refactor/validation kỹ thuật nhỏ, ≤2 file một module | `implement-feature` rút gọn; một dòng rule, không tạo spec nghiệp vụ | Test đích + `node scripts/verify.mjs --quick`; nếu 3–8 file thì lên M và chạy full verify |
| Feature mới, kể cả làn S | Domain spec 14 mục → `speckit-specify` → `clarify` → `plan` → data model/contract → `tasks` → `analyze` → `implement` → `converge` đến sạch | Test theo AC; verify đúng làn. Chạy FE/browser chỉ khi có tác động tương ứng |
| Đổi nghiệp vụ đã có | `clarify-business` trước code; sửa nguồn business rule/spec và phân loại làn | Test rule bị ảnh hưởng + verify theo làn; converge khi là thay đổi feature theo Spec Kit |
| DB/entity/migration/query | `plan-change` làn L + `update-database`; migration mới, không sửa migration đã chạy | Migration/query PostgreSQL Testcontainers + `node scripts/verify.mjs`; kiểm `DATA_DICTIONARY.md`/`ERD.md` |
| API/DTO/auth/response contract | `update-api`; API contract làn L theo repo; xem `CROSS_REPO_MAP.md` | Controller/contract test + full `node scripts/verify.mjs` (có OpenAPI snapshot); FE test nếu được phép sửa |
| Thay đổi Web kế hoạch giáo dục | Đọc AGENTS của repo FE, module docs; nếu đổi feature thì theo Spec Kit ở source of truth BE | `npm run test:edu` khi lặp; `npm run verify` ở cổng FE cuối; Playwright chỉ cho luồng browser cần BE/auth thật |
| Thay đổi harness/CI/hook/scripts/verify | `plan-change` làn L trước khi sửa harness; `fix-bug` nếu chỉ sửa lỗi harness | Chạy file test harness liên quan dưới `scripts/__tests__/` rồi cổng verify đầy đủ bị ảnh hưởng |
| Chỉ sửa tài liệu/Spec Kit | Workflow tài liệu tương ứng; không tạo source test | `node scripts/check-ai-layer.mjs`; không chạy full app verify nếu không đổi source/harness |
| Review | `review-code`; không sửa source trừ khi user yêu cầu fix | Harness tương ứng để xác nhận phát hiện; ghi rõ phần chưa chạy |

### Ý nghĩa các lệnh chính

- BE `node scripts/verify.mjs --quick`: ánh xạ file Java đổi sang test gần nhất và luôn thêm ArchUnit; nếu đổi `pom.xml`/resources hoặc không ánh xạ được test thì tự nâng lên rộng hơn. Dùng cho làn S, không dùng để khẳng định feature lớn sạch.
- BE `node scripts/verify.mjs`: full `mvnw verify`, gồm test, ArchUnit, OpenAPI snapshot và Spotless. Đây là cổng chuẩn cho làn M/L và sau thay đổi source cuối.
- FE `npm run test:edu`: test giáo dục tập trung. `npm run verify`: toàn bộ test FE, lint, format, design/API checks và production build.
- FE browser: `npm run test:e2e:integration` hoặc `npm run test:e2e:system`; cần BE thật, Keycloak, Docker/PostgreSQL và `E2E_DEV_PASSWORD` trong môi trường cục bộ. Không chép secret vào chat, log hoặc repo. Chỉ chạy khi có kịch bản cần kiểm tra tích hợp/browser thật.
- `node scripts/check-ai-layer.mjs`: sau khi sửa `.ai/`, `.claude/`, `.agents/` hoặc tài liệu theo quy định repo. Cảnh báo link cũ phải được phân biệt với lỗi; không sửa tài liệu ngoài phạm vi chỉ để xóa cảnh báo.
- `./mvnw.cmd '-Dtest=SomeTest' test`: chạy test tập trung trong lúc phát triển, không thay cổng verify cuối mà lane yêu cầu.

Không chạy mọi harness cho mọi task. Chạy test gần vùng thay đổi trong vòng lặp, sau đó đúng cổng cuối theo làn. Test Docker bị skip là chưa kiểm chứng; log phải có exit code/output thật. Không tạo thêm harness/dependency, không chạy E2E khi không có case tương ứng, không báo coverage phần trăm nếu chưa đo bằng công cụ coverage.

## Prompt nền dùng cho mọi task

```text
Hãy xử lý task sau trong repo [repo]: [mô tả]
Mã Jira: [mã hoặc “chưa có”]. Module: [module]. Phạm vi được phép sửa: [phạm vi].

Trước hết đọc AGENTS.md và workflow/harness liên quan. Phân loại task và làn S/M/L theo repo; không hạ làn để tiết kiệm thời gian. Chỉ dùng harness có sẵn, chọn test tập trung trong lúc sửa và đúng verify gate cuối theo làn. Không thêm dependency/harness, không sửa repo khác, không commit/push/deploy nếu chưa được phép.

Nếu là feature mới: chạy đủ Domain spec → Spec Kit → clarify → plan/data model/contract → tasks → analyze → implement → converge; lặp implement/verify/converge đến khi không còn gap. Nếu là bug: đối chiếu rule và incident memory, chứng minh root cause, thêm regression đúng phạm vi và cập nhật Attempts khi cần. Nếu là đổi nghiệp vụ chưa rõ, hỏi user và cập nhật nguồn nghiệp vụ trước code.

Trước khi code, nêu ngắn gọn loại task/làn, rule hoặc AC nguồn, file dự kiến và harness cuối sẽ chạy. Nếu cần user duyệt theo quy định làn L, hoàn tất artifacts để user duyệt trước khi sửa source. Khi xong, ghi lệnh + kết quả thật, fail/skip/chưa chạy, giới hạn và cập nhật tài liệu verification theo repo. Không tuyên bố pass nếu chưa có output sau lần sửa source cuối.
```

## Các prompt theo loại task

### 1. Bug / test đang fail

```text
Fix bug này: [triệu chứng/nguyên văn lỗi]. Mong đợi theo [rule ID/AC].
Tra ISSUE_INDEX và incident Attempts trước; tái hiện bằng fixture tối thiểu; xác định root cause và phạm vi dữ liệu/quyền/API trước khi sửa. Dùng workflow fix-bug, viết regression cho lỗi này theo hướng fail trước/pass sau khi quy định repo yêu cầu. Chạy test gần module trong vòng lặp, rồi verify S bằng `node scripts/verify.mjs --quick` hoặc M/L bằng `node scripts/verify.mjs`. Nếu đã thất bại 3 lần thì dừng và báo Attempts theo AGENTS.md.
```

### 2. Feature mới

```text
Thêm feature [mô tả] cho [actor] để đạt [outcome]. Quy tắc đã chốt: [liệt kê nguồn/Rule ID]; chưa chốt: [điểm cần hỏi].
Áp dụng feature-spec-loop.md, kể cả task nhỏ: Domain spec đủ 14 mục, Spec Kit, clarify cho đến hết điểm nghiệp vụ chặn, plan, data model/contract phù hợp, tasks có test trace AC, analyze trước implement. Implement hết tasks; chạy harness phù hợp theo lane; converge và lặp đến zero findings. Không tự biến giả định thành business rule.
```

### 3. Đổi nghiệp vụ hiện có

```text
Đổi rule [ID/luồng] từ [hiện tại] thành [mong muốn], phạm vi [module/actor]. Trước code, đối chiếu BUSINESS_RULES, module card và code; nếu khác nhau hoặc còn OPEN/PENDING thì dùng clarify-business.md và cập nhật nguồn được chọn. Ghi migration/compatibility/role-scope impacts. Chọn lane; test các hành vi cũ cần giữ và rule mới; chỉ chạy FE/APP harness nếu có client impact và quyền sửa tương ứng.
```

### 4. DB/schema/migration

```text
Thay đổi dữ liệu [bảng/entity/query] để [mục tiêu]. Đây là lane L: đọc escalation + plan-change.md và update-database.md, phân tích dữ liệu cũ/backfill/rollback/campus scope/index/caller trước; tạo plan để user duyệt. Không sửa migration đã chạy; tạo migration mới. Thêm/điều chỉnh PostgreSQL integration tests cho migration và query, kiểm dictionary/ERD, rồi chạy `node scripts/verify.mjs` (không tính test bị Docker skip là pass).
```

### 5. API/DTO/authorization contract

```text
Đổi API [endpoint/DTO/auth/error] vì [lý do]. Xác định consumer cụ thể qua CROSS_REPO_MAP; phân loại breaking/non-breaking và nêu tác động FE/APP. Theo update-api.md; API contract là lane L, plan/approval trước code. Thêm controller + service permission/scope tests; chạy `node scripts/verify.mjs` để kiểm full suite và OpenAPI snapshot. Chỉ cập nhật snapshot bằng lệnh được workflow cho phép và review diff. Không sửa client repo nếu chưa được user cho phép.
```

### 6. Web/App client

```text
Sửa [repo] ở phạm vi [module/page] theo contract [endpoint/spec]. Đọc AGENTS/workflow của client; xác nhận BE source of truth và quyền sửa repo hiện tại. Khi lặp, chạy test module (Web giáo dục: `npm run test:edu`); cuối chạy `npm run verify`. Chỉ chạy Playwright integration/system nếu thay đổi tương tác browser/auth/API và đã chuẩn bị BE, Keycloak, Docker cùng biến môi trường local; ghi rõ case nào chạy. Không log hoặc yêu cầu secret qua chat.
```

### 7. Harness/CI/gate

```text
Điều tra harness [lệnh/file] vì [lỗi/thiếu kiểm tra]. Không sửa nghiệp vụ để né gate. Nếu thay scripts/CI/hook thì lane L và plan-change trước. Tạo test cho chính hành vi harness (selection, exit code, skip/fail reporting), chạy test harness liên quan, rồi chạy gate mà nó điều phối. Báo rõ thay đổi làm gate nhanh/chậm hơn và các loại rủi ro nó còn kiểm.
```

### 8. Tài liệu/Spec Kit hoặc review

```text
Tác vụ tài liệu/review: [mục tiêu]. Giữ nguyên nguồn quyết định và đánh dấu nội dung lịch sử nếu đã bị quyết định mới thay thế. Không đổi source nếu không được yêu cầu. Với docs/.ai/.agents/.claude, chạy `node scripts/check-ai-layer.mjs`; không chạy BE/FE full tests nếu không đổi source/harness. Với review, chỉ báo finding có evidence file/line, severity và tác động; không tự sửa.
```

## Mẫu kết thúc task

```text
Kết quả: [đã làm/đang chờ approval]
Lane: [S/M/L]. Spec/tasks: [IDs hoàn thành và findings còn lại].
Harness: [lệnh] — [PASS/FAIL/SKIP + số liệu thật].
Chưa kiểm chứng / giới hạn: [nêu rõ, hoặc “không”].
Tài liệu cập nhật: [đường dẫn]. Commit/push/deploy: [không thực hiện hoặc theo ủy quyền cụ thể].
```
