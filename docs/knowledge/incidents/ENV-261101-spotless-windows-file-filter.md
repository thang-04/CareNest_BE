---
id: ENV-261101-spotless-windows-file-filter
type: env
modules: [deployment]
rules: []
status: fixed
date: 2026-11-01
keywords: [spotlessFiles, Windows, spotless apply, BUILD SUCCESS, file filter, regex]
similar_to: []
---

# ENV-261101-spotless-windows-file-filter — Spotless apply báo thành công nhưng không sửa file được chọn

## Symptom
Sau khi bổ sung MVC test cho tiếp nhận trẻ, `node scripts/verify.mjs --quick` chạy 85 test không có failure/error/skip nhưng fail Spotless tại `ChildEnrollmentControllerTest.java`. Hai lần gọi `spotless:apply` với `-DspotlessFiles=src/test/java/com/carenest/controller/child/ChildEnrollmentControllerTest.java` đều trả `BUILD SUCCESS`; file vẫn giữ nguyên và `spotless:check` tiếp tục báo format diff.

## Điều kiện tái hiện
Windows/PowerShell, Spotless Maven plugin 3.10.3. Chỉ định đường dẫn tương đối với dấu `/` trong `spotlessFiles` để giới hạn apply vào một file test đang đổi.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Chạy `node scripts/verify.mjs --quick` sau khi sửa test | 85 test, 0 failure/error/skip; Spotless fail | Test pass không đồng nghĩa toàn bộ cổng pass |
| 2 | Chạy `mvnw.cmd "-DspotlessFiles=src/test/java/com/carenest/controller/child/ChildEnrollmentControllerTest.java" spotless:apply` | `BUILD SUCCESS`, file không đổi | Chưa chứng minh bộ lọc có khớp file |
| 3 | Lặp lại cùng lệnh apply, rồi đối chiếu format diff | Vẫn không đổi; check còn lỗi | Lặp lại lệnh thành công không chứng minh file đã được format |
| 4 | Đọc `META-INF/maven/plugin.xml` và bytecode `AbstractSpotlessMojo` trong plugin 3.10.3 bằng `javap -c -p` / `javap -v -p` | `spotlessFiles` nạp vào `filePatterns`, biên dịch bằng `Pattern.compile`, khớp bằng `matcher(file.getAbsolutePath()).matches()` | Đây là regex khớp toàn đường dẫn tuyệt đối, không phải đường dẫn tương đối hay glob |
| 5 | Chạy `mvnw.cmd "-DspotlessFiles=.*ChildEnrollmentControllerTest.java" spotless:apply` | Exit 0; log chỉ đúng file test, `1 files clean - 1 were changed`, `0 skipped`; file thực tế được format lại | Prefix `.*` khớp phần đường dẫn tuyệt đối; lần này filter thực sự chọn file |
| 6 | Chạy lại `node scripts/verify.mjs --quick` sau khi format | `VERIFY PASS quick`, 85 test, 0 failure/error/skip; ArchUnit và Spotless đều đạt | Xác nhận fix có hiệu lực tại cổng chất lượng; không chỉ dựa vào exit code của apply |

## Root cause
`spotlessFiles` là regex khớp **toàn bộ đường dẫn tuyệt đối** của `File.getAbsolutePath()` bằng `Matcher.matches()`. Bộ lọc ban đầu chỉ chứa đường dẫn tương đối, lại dùng `/` trong khi đường dẫn native Windows dùng `\`, nên không chọn file nào. Plugin vẫn có thể trả `BUILD SUCCESS` với tập file rỗng.

Bằng chứng trực tiếp từ plugin local `com.diffplug.spotless:spotless-maven-plugin:3.10.3`: `META-INF/maven/plugin.xml` ánh xạ `${spotlessFiles}` sang `filePatterns`; `AbstractSpotlessMojo.collectFiles` tách chuỗi bằng dấu phẩy và biên dịch `Pattern`; `lambda$collectFiles$1` gọi `Pattern.matcher(file.getAbsolutePath()).matches()`. Lệnh ở attempt 5 thực tế sửa đúng một file sau hai lần bộ lọc cũ không sửa gì.

## Fix
Dùng regex khớp phần đầu đường dẫn bằng `.*` rồi tên file chính xác. Để giới hạn cả vị trí và chạy được trên Windows/Linux, PowerShell truyền nguyên một argument:

```powershell
.\mvnw.cmd '-DspotlessFiles=.*[\\/]src[\\/]test[\\/]java[\\/]com[\\/]carenest[\\/]controller[\\/]child[\\/]ChildEnrollmentControllerTest[.]java' spotless:apply
```

`[\\/]` chấp nhận hai dấu phân cách đường dẫn; `[.]` khớp dấu chấm của đuôi file. Bộ lọc ngắn ở attempt 5 đã sửa đúng một file trong lượt này; bộ lọc đầy đủ ở trên tránh chọn thêm file cùng tên tại vị trí khác. Không sửa `pom.xml`, cấu hình plugin hay dependency.

## Regression test
Sau khi format, `node scripts/verify.mjs --quick` trả:

```text
VERIFY PASS quick | tests 85 fail 0 err 0 skip 0 | archunit ok | snapshot - | spotless ok | 19s | log target/verify.log
```

Trong đó `ChildEnrollmentControllerTest` chạy 65 ca. Quick không chạy snapshot; đây là kiểm chứng formatter và cổng quick trên Windows, không thêm test mới cho plugin bên ngoài. Cần kiểm `node scripts/check-ai-layer.mjs` sau lần cập nhật knowledge cuối.

## Ảnh hưởng
Ảnh hưởng thao tác format và cổng chất lượng trên Windows; không đổi API hay dữ liệu nghiệp vụ. Không sửa repo FE/APP.

## Lesson
`BUILD SUCCESS` của lệnh format có bộ lọc chưa đủ chứng minh bộ lọc đã chọn file; cần kiểm tra file thực tế và chạy lại cổng chất lượng.
