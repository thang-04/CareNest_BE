# Non-Functional Requirements

Nguồn chính: `Non_Req_v3.docx` (team, 28/09/2026; ID dưới đây do docs gán). Status theo quy ước `docs/context/PROJECT_CONTEXT.md`.

| ID | Nhóm | Yêu cầu | Status |
| --- | --- | --- | --- |
| NFR-SCALE-01 | Quy mô | 1 trường, 2 campus, 24 lớp, ~506 trẻ, ~60 nhân sự + phụ huynh | CONFIRMED |
| NFR-ARCH-01 | Kiến trúc | 1 instance BE + 1 PostgreSQL; không scale-out | ACCEPTED |
| NFR-PERF-01 | Hiệu năng | 95% request API thông thường ≤ 2s với ≤ 50 người dùng đồng thời (không tính gọi AI, upload) | PROPOSED |
| NFR-PERF-02 | Hiệu năng | Dùng được với 50 người dùng đồng thời trên 1 deployment, dữ liệu demo | PROPOSED |
| NFR-PERF-03 | Hiệu năng | Nhập điểm danh/quan sát cả lớp (20–25 trẻ) trong 1 thao tác lưu; cao điểm buổi sáng trước cut-off (P-03) | PROPOSED |
| NFR-USE-01 | Usability | ≥ 80% người dùng thử hoàn thành task chính không cần trợ giúp (điểm danh, xác nhận suất, xem định lượng bếp, xem con) | PROPOSED |
| NFR-USE-02 | Usability | Thông báo xác nhận/lỗi rõ ràng sau mọi thao tác tạo/sửa/duyệt/gửi; UI tiếng Việt | PROPOSED |
| NFR-SEC-01 | Bảo mật | Chỉ người đã đăng nhập truy cập chức năng được bảo vệ; HTTPS; mật khẩu băm | ACCEPTED |
| NFR-SEC-02 | Bảo mật | Chỉ đọc/sửa dữ liệu theo role + campus/lớp/trẻ được gán; phụ huynh chỉ thấy con mình (AUTH-*) | ACCEPTED |
| NFR-SEC-03 | Bảo mật | Mọi truy cập ngoài scope trong test đều bị từ chối | ACCEPTED |
| NFR-SEC-04 | Privacy | Dữ liệu sức khỏe/nhạy cảm không xuất hiện trong thông báo lỗi và log thường; AI chỉ nhận dữ liệu tối thiểu, bí danh (AI-04, AI-05) | ACCEPTED |
| NFR-REL-01 | Tin cậy | Thao tác lỗi không để điểm danh, số suất, phê duyệt ở trạng thái nửa vời (transaction) | PROPOSED |
| NFR-REL-02 | Tin cậy | Gửi lặp không tạo bản ghi trùng (idempotent) | ACCEPTED (ATT-01b) |
| NFR-REL-03 | Tin cậy | AI lỗi/timeout/output không dùng được ⇒ báo người dùng, tiếp tục thủ công; AI output không bao giờ được coi là đã duyệt | CONFIRMED (AI-02, AI-03) |
| NFR-MAINT-01 | Bảo trì | Thay đổi schema có version, tái lập được (Flyway) | ACCEPTED |
| NFR-MAINT-02 | Bảo trì | Build + chạy test tự động theo setup trong README | ACCEPTED |
| NFR-MAINT-03 | Bảo trì | Code riêng của AI/storage provider nằm ngoài workflow nghiệp vụ (`integration/`) | ACCEPTED (AI-06) |
| NFR-INT-01 | Tích hợp | Web + mobile dùng chung REST API, response thống nhất (`ERROR_CONTRACT.md`) | ACCEPTED |
| NFR-INT-02 | Tích hợp | File/ảnh qua BE, giới hạn theo role và liên kết trẻ/lớp/campus | PROPOSED |
| NFR-INT-03 | Tích hợp | Mọi gọi AI đi qua BE; AI là tùy chọn, workflow lõi chạy khi AI tắt | CONFIRMED |
| NFR-INT-04 | Tích hợp | Hệ thống ngành: chỉ xuất dữ liệu, không kết nối DB trực tiếp | CONFIRMED (EX-01, LI-01) |
| NFR-AVAIL-01 | Availability | Không yêu cầu 24/7 HA, không DR đầy đủ | CONFIRMED (exclusion) |
| NFR-OPS-01 | Vận hành | Backup PostgreSQL định kỳ (tần suất PENDING) | PROPOSED |
| NFR-OPS-02 | Vận hành | Audit hành động nhạy cảm (`docs/modules/audit.md`) | ACCEPTED |
| NFR-TZ-01 | Khác | `Asia/Ho_Chi_Minh` cho ngày nghiệp vụ | ACCEPTED |

Còn thiếu trong Non_Req_v3 (cần team bổ sung): availability mục tiêu cho demo, backup/restore, audit, ẩn danh dữ liệu khi gửi AI.
