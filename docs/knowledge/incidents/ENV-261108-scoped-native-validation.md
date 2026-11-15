---
id: ENV-261108-scoped-native-validation
type: env
modules: [education-plan]
rules: [EDU-60]
status: fixed
date: 2026-11-08
keywords: [Windows, Spotless, spotlessFiles, regex, mvnw.cmd, cwd, SSR, portal]
similar_to: []
---
# Native checks trên Windows: regex và working directory

## Symptom
Scoped Spotless apply CSV không match file; glob báo dangling meta character; regex chứa pipe qua mvnw.cmd bị cmd phân tách. Lệnh FE nhầm cwdBE không tìm file và npx có thể tải tool vào cache. SSR editable table với DnD portal thiếu document.

## Attempts
1. CSV đường dẫn forward-slash: Maven thành công nhưng không format file nào; full check sau đó vẫn báo source vừa sửa.
2. Đọc plugin.xml trong JAR Spotless3.10.3, filePatterns là regex; goalhelp không có, glob sai, pipe bị cmd xử lý.
3. Dùng từng regex `-DspotlessFiles=.*TenFile[.]java` qua native mvnw.cmd: xác nhận log keeping1fileclean, apply/check11file đạt.
4. FE command nhầmcwdBE: không dependency file nào đổi; bỏ npx discovery, dùng binary node_modules/.bin và workdirFE, test278/lint/build đạt.
5. SSR chỉ kiểm readonly tree; editable DnD/geometry dùng native headless Chromium với CSS thật, không sửa production để chiều SSR; selectors status phải có scope vì DnD có live regions.

## Root cause / Fix
Spotless file selector là regex khớp path Windows; .cmd còn qua một tầng shell. Tách từng filename regex không pipe, không dùng glob/CSV. Native FE commands cần cwdFE và binarylocal. Không dùng scoped result thay full-repo result; lỗi formatting ngoài delta phải báo rõ.

## Regression / Evidence
TEMP/carenest013-format-*.log và check-*.log:11/11file. FE278tests và browser fixture1440/390/localdraft đạt. Không đổi pom/package/lock/formatter config hoặc bỏ test/hook.

## Bổ sung sau review08/11/2026
User cho phép fix full-repo format: spotless:apply sửa11file còn lỗi, fullspotless:check +94native tests đạt. LiveKeycloak E2E tìm được credential envignored, không cần hỏi/đưa secret vào log. Database mới chỉ có seed2026 nên fixture cũ cần tự tạo năm/class2027; issue cần revisionheader và theme tối thiểu2tuần theo policy. Các lỗi này là setup/test-contract, không sửa production để pass. Selector `.ga-theme-edit-fixed` có hai bảng nên assertion đơn phải scope `.first()`. Fixture mới guard prefixDBriêng, đạt2/2 rồi cleanup và xóaDBtạm; giữ BE8080 và container cũ. Log TEMP/carenest013-keycloak-e2e.log.
