# Research — 2026-10-27

- EduClassDocServiceImpl kiểm assignment, năm ban hành và khóa parent khi save/submit; tái dùng policy cho media.
- FE classPlan.js themeActivities dùng goalId#index; giữ ID, fingerprint toàn hàng để tránh chuyển nhầm tệp khi sửa/thay thứ tự.
- Browser hỗ trợ ảnh/video/PDF; Office chọn download, không converter hay public viewer mới.
- Metadata DB và S3 private theo UUID; native IndexedDB Blob cho mock thay vì localStorage.
- Giới hạn stream/extension/magic; Office ZIP download opaque, không giải nén lên filesystem. Blob preview revoke khi unmount/chuyển selection.

- User bổ sung S3 và đồng ý SDK v2; chọn SDK 2.57.3 theo release AWS chính thức. Private S3 proxy qua backend sau scope check; credentials default provider chain, bucket/envconfig chưa supplied. Không dùng presigned public URL, không dùng local volume. Chi tiết ADR-0018.

