# Workflow đổi API Backend

1. Xác định caller và client chịu ảnh hưởng, quyền truy cập, request/response/error và tương thích với hành vi cũ.
2. Kiểm tra contract hiện có; `docs/contracts/openapi.yaml` đang được dự kiến nhưng chưa tồn tại. Không viện dẫn nó như nguồn chuẩn cho đến khi được tạo.
3. Thống nhất thay đổi cần thiết với FE/APP, rồi cập nhật endpoint/DTO/contract và test theo source thực tế. Theo mục 7–8 của `../../docs/backend-coding-guide.md`: prefix lấy từ `carenest.api.prefix`, response `{code, desc, data}` với `code` từ `ApiCode` trùng HTTP status; exception mới phải được map trong `GlobalExceptionHandler`.
4. Kiểm tra trường hợp lỗi, auth và hành vi client; ghi rõ phần client chưa được kiểm thử nếu không có checkout/runtime.

