# Verification

1. Full verify BE/FE, Docker running cho real PostgreSQL tests.
2. Assigned teacher upload ảnh/video/PDF/tệp theo saved activity; reload đúng, same request retry không duplicate.
3. Từ chối tệp rỗng/sai magic/quá cỡ, ngoài lớp, pending/approved, stale binding; không mất dữ liệu trước.
4. Đổi activities → cần gắn lại; explicit rebind; detach giữ tài nguyên.
5. Presenter từ theme/day đúng MT/activity; keyboard/fullscreen/back, empty/loading/error; Office tải, video không autoplay.
6. Regression tài liệu cũ save/export/submit/approve. Analyze read-only; Converge append missing tasks ID mới cuối file, không mất task cũ.

## Results 2026-10-27
- BE full: VERIFY PASS full | tests 390 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 137s | log target/verify.log.
- FE full: VERIFY PASS full | npm run verify | log verify.log. Có cập nhật PDF fallback sau lần này, cần chạy gate lại.
- Browser mock localhost5188: upload image/PDF, reload giữ metadata/binary, đổi activities → unbound, explicit rebind, detach-confirm giữ tệp và rebind, presenter theme/legacy day, keyboard arrows và fullscreen. Tài liệu demo được khôi phục nội dung gốc sau ca reorder.
- IAB không render native PDF trong iframe; chuyển native object có fallback. Không ghi nhận PDF inline-render đã pass. S3 live chưa cấu hình; SDK mock, PostgreSQL integration và protected binary Range206 có test riêng.
- Screenshots target/teaching-media-demo.png, target/teaching-day-demo.png; không dùng ảnh PDF trắng làm bằng chứng thành công.


## Final review / cloud evidence
Review làn L qua subagent đã hoàn tất: không còn concrete P1/P2 trong production path được review. Kiểm multipart thật bằng LearningResourceControllerTest (3/3) sau sửa @RequestPart; snapshot đã sinh lại. Fresh BE gate sau contract: 391 tests, fail0 err0 skip0, archunit/snapshot/spotless ok110s; đang chạy lần cuối sau thêm smoke class.

Live S3: StorageS3Smoke1/1 pass, ảnh/PDF/text/binary1MiB put→head/get bytes→anonymous denied→delete→404,4objects cleaned. User cho phép dùng cấu hình ngoài repo; secret chỉ environment child process và không có trong log. Đây là storage component với S3 thật, không phải real browser-BE-cloud E2E. Native PDF rendering trong IAB vẫn chưa kiểm chứng; feature-detect/fallback download được bổ sung.

Final gates sau source cuối: BE VERIFY PASS full | tests 391 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 110s | log target/verify.log; FE VERIFY PASS full | npm run verify | log verify.log. Cloud smoke self-contained chạy lại:1 test,0fail/0error/0skip,8.722s, all objects cleaned. Review không concreteP1/P2. T001..T030 đã xử lý (các task superseded có ghi mapping, không mất task cũ).


Final after parser parity fix: BE VERIFY PASS full | tests 392 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 121s | log target/verify.log. FE VERIFY PASS full | npm run verify | log verify.log; education suite31tests. Tiny parser delta review: no concrete P1/P2. Live S3 StorageS3Smoke1/1 passed before this parser-only correction; storage source unchanged. All T001..T031 resolved, original task IDs/text retained with superseded mapping notes.

