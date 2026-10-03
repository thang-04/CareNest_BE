# Test Strategy

Nguồn chuẩn: `docs/backend-coding-guide.md` mục 14. Stack: JUnit, Mockito, Spring Boot Test (`spring-boot-starter-webmvc-test`), Testcontainers PostgreSQL. Chạy: `./mvnw test`.

## Tầng test

| Tầng | Phạm vi | Công cụ | Khi nào bắt buộc |
| --- | --- | --- | --- |
| Unit | Domain rule, tính toán (MealCount, định lượng, trend) | JUnit + AssertJ | Mọi rule có ID |
| Application/integration | Service + DB thật, transaction, scope filter | Spring Boot Test + Testcontainers | Use case ghi dữ liệu; mọi query có scope |
| Controller | `{code, desc, data}` + HTTP status, validation | WebMvc test; `ResponseContractTest` khi đổi handler | Mỗi endpoint |
| Regression | Tái hiện bug đã fix | Tầng phù hợp nhất | Mọi bug fix |

## Kịch bản bắt buộc (từ rủi ro đã biết)

- Trẻ đến muộn sau khi số suất đã xác nhận ⇒ số suất đánh dấu thay đổi, cần xác nhận lại, bếp vẫn thấy bản đã xác nhận (CMR-01).
- Bếp/định lượng chỉ dùng số đã xác nhận (CMR-02).
- User campus A không đọc/sửa được dữ liệu campus B; GV không đọc lớp khác; PH không đọc trẻ khác (CMR-03).
- Trẻ chuyển lớp giữa kỳ: thống kê theo enrollment tại ngày (CMR-04).
- Input gửi AI không chứa tên/ngày sinh trẻ (CMR-05).
- AI tắt/timeout ⇒ luồng nghiệp vụ vẫn hoàn thành (AI-03).

## Quy ước

- Tên test mô tả hành vi: `shouldRequireReconfirmationWhenAttendanceChangesAfterConfirmation`.
- Có thể ghi rule ID trong `@DisplayName` để trace: `@DisplayName("NUT-03: confirmed meal count is immutable")`.
- Dữ liệu test giả; builder/fixture theo module.
