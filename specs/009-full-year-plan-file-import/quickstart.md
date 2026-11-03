# Validation — Feature009

**Quy tắc hiện hành**: hai chế độ thay nội dung theo spec/domain cập nhật02/11; evidence/parser và mô tả additive dưới đây là lịch sử trước thay đổi. Đã có evidence phase2 FE bên dưới; chưa có converge cuối hoặc E2E BE thật.

## Phase2 BE
Run targeted parser/service tests bằng mvnw hiện có, RED trước code rồi GREEN sau. Chạy `node scripts/verify.mjs` sau thay đổi cuối (full tests/ArchUnit/OpenAPI/Spotless); `node scripts/check-ai-layer.mjs` cho docs. Sample probe dùng file cục bộ ở máy user, chỉ in counts, không đưa nội dung/người ký vào log hoặc repo. Ghi oracle độc lập và giới hạn khi đã đo. Review độc lập và user review trước Web.

## Phase3 FE sau review
Run tests education-plan + `node scripts/verify.mjs`; browser source/CSS thật375/768/1440: row/button baseline/overflow/focus/errors, preview metadata/lịch/năm dời/age conflict, confirm giữ nhập tay/ticks, cancel/race/lostlease. Real-BE E2E file/save/reopen và old formats, không ghi credential hoặc copy file thật vào fixture.

## Evidence
Phase2 đã có output thật sau source/test cuối: `VERIFY PASS full | tests 659 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 278s | log target/verify.log`. Log full giữ tại `target/feature009-final-full.log` trước lần verify hồ sơ cuối. Snapshot thay53dòng additive chỉ3schema mới và3field metadata/themes/breaks, không đổi endpoint/request/schema request cũ.

- TDD domain: RED4fail → GREEN4, cùng10legacy parser đạt14/14. Preview đầu RED5fail→GREEN5; bổ sung regressions metadata warning/boundary/multiple ranges/docx. Trước source boundary cuối targeted+snapshot31/31 đạt; full659 cuối đã chạy11test FullYearPlanFileImportTest và4test YearPlanDomainVariantTest, fail/skip0.
- Metadata sau bảng: probe với classes trước fix RED AssertionError, sau fix GREEN; regression Java `ageAfterGoalTableIsNotMetadata` nằm trong full cuối. Metadata trong bảng không được lấy làm nhóm tuổi; cảnh báo metadata được copy sau khi parse.
- Sample gốc cục bộ qua assertions trên target/classes cuối:5lĩnh vực83MT203nội dung389ticks,10chủ đề đúng từng cặp ngày/viết tắt,2nghỉ16–20/02/2026 và27/04–01/05/2026,metadata2025-2026/36–48tháng,0warning. Số MT và10cặp ngày/viết tắt đối chiếu độc lập; content/ticks là counts reader, không gọi là oracle độc lập. Không copy source/người ký vào repo.
- Hai vòng review độc lập đã phát hiện/sửa warning-copy-order, multiple-date-range truncation và metadata sau bảng; review cuối không còn lỗi chặn. Snapshot full không skip, ArchUnit/Spotless đạt.
- Chưa làm phase3Web: merge/preview toàn bộ và chặn nhóm tuổi/dời năm trên UI, hàng nút đồng kiểu, phần1dạngbảng, browser/real-BE E2E/save-reopen/converge. Không đánh dấu toàn feature hoàn tất, không commit/push/deploy. Cần user review backend theo plan trước Web.


## Validation delta hai chế độ — sau review phase1
1. FE: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/full-year-file-import.test.mjs tests/education-plan/import-goal-file-modal.test.mjs`; cần RED trước model/modal và GREEN sau.
2. Form tổng hợp có title/mô tả/goals/themes/breaks/ticks; import file đầy đủ và file chỉ bảng mục tiêu, chọn từng mode. Full: phần thiếu trống; present: phần thiếu giữ, phần có thay nguyên phần; không còn dòng cũ trong phần thay. Preview đúng trước apply, Hủy/error không đổi form.
3. Năm đích/range cấu hình cố định, khác source year dời lịch đúng; thiếu range khóa apply; ngoài năm cảnh báo; group mismatch bị chặn. Không đổi owner/status/version/revision/session, không gửi mode trong API.
4. Giữ domains nhưng thay themes: remap theo mã, báo liên kết không còn đích/ambiguity; không tick ID cũ bị xóa. Đổi mode khi modal mở, đổi file nhanh, late response/lostlease/double click không apply sai.
5. Browser375/768/1440: radio/label/focus/no body overflow; ticks QH-DN-BH ba dòng, T&M không cắt, chuột/bàn phím không đổi mã. Apply→leave→Không lưu phục hồi bản server; apply→Lưu→reopen kiểm persistence khi BE thật sẵn. Không dùng harness giả để khẳng định BE thật.
6. FE full verify/check-ai-layer và BE check-ai-layer cho docs; log rõ lỗi targetName/additive test cũ và cách thay expectation theo quy tắc mới, không xóa test để che lỗi. Sau implement chạy converge, append task thiếu rồi lặp, không sửa task history.

Phase1 docs: chỉ cập nhật spec/tri thức/plan, chưa có test GREEN cho hai mode. User duyệt kế hoạch và tài liệu BE; theo plan, dừng review phase1 trước T013..T017. Không source/API/DB BE đổi, không commit/push.

### Evidence phase1 delta02/11/2026
- Domain14, FR13, task delta9 (T012..20), tất cả20task IDs unique. Quality checklist16checked; manual cross-artifact analysis13/13 FR có task,0critical/high blockers. Không code implementation hoặc chạy converge.
- BE check-ai-layer:0lỗi/12cảnh báo hiện hữu; git diff src/pom/.specify rỗng. FE check-ai-layer/verify và UI wrap ghi riêng trong FE:docs/quality/VERIFICATION.md; không lấy kết quả parser659 cũ thay cho mode Web mới.
- T012 hoàn tất tài liệu; T013..20 còn mở. Cổng phase1: sẵn sàng review, chưa chuyển phase2 logic nhập. Không commit/push.

## Evidence phase2 delta02/11/2026 — chờ review
- User duyệt phase1. T013..T017 complete; T018..T020 còn mở, chưa converge.
- Model11/11 sau RED10fail; modal7/7 sau RED3fail; targeted28/28; suite edu133/133. `node scripts/verify.mjs` FE: `VERIFY PASS full | npm run verify | log verify.log`, tổng369tests fail0/skip0 và lint/format/design/build đạt. Một cảnh báo react-hooks ref hiện hữu, không phải error. Sau ghi hồ sơ cuối chạy lại cổng/check-ai-layer, output bàn giao là kết quả cuối.
- Browser source/CSS thật với dịch vụ/lease/save giả: full/present desktop1440/mobile375, mode preview/cancel/no mutation, fields fixed/title/description/themes/goals/ticks đúng, missing system-range khóa apply, keyboard tick và readonly QH-DN-BH xuống dòng,0lỗiJS/no body overflow. Artifacts cục bộ FE:node_modules/.cache/goal-ui-shots/file-full-1440.png, file-present-375.png; FE:node_modules/.cache/goal-import-browser.log. Không commit artifacts.
- E2E source hai test EDU-46 đã đổi cách chọn present để giữ lịch/metadata khi file chỉ mục tiêu, xác nhận mới “Áp dụng vào kế hoạch”, kỳ vọng thay cây thay bỏ trùng additive; `node --check` đạt. BE/Keycloak có listener nhưng E2E_DEV_PASSWORD thiếu: không chạy real-BE login/upload/save/reopen và không khẳng định persistence mới đã được kiểm.
- Incident FE targetName của nhánh additive được đóng do quy tắc mới thay nhánh đó, vẫn giữ fixture mã/tên xung đột và regression ở cả mode; không xóa test hoặc thêm getter giả để né gate.
- Dừng review phase2 trước phase3/converge theo kế hoạch được duyệt. Chưa commit/push, production BE không đổi.

- Phase2 review độc lập read-only không phát hiện Critical/Important/Minor mới trong delta, reviewer chạy lại18/18 model/modal và đọc save mapper; chưa chạy full/BE E2E độc lập. E2E file year-plan-import có thêm ca full xóa thiếu/lưu/mở lại bên cạnh hai ca present, syntax đạt; cả ba chưa chạy vì thiếu credential, không nhận là persistence đã kiểm.
