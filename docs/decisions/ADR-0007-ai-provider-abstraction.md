# ADR-0007 — Trừu tượng hóa AI provider và bảo vệ dữ liệu trẻ

- Status: ACCEPTED (abstraction, guide mục 2, 4, 13.3) · **OPEN** (chọn provider — P-02)
- Date: 2026-10-03
- Liên quan: AI-04..06, module ai-assistance

## Bối cảnh
Chưa chốt external API hay local model. Dữ liệu trẻ em nhạy cảm. Cần đổi provider không ảnh hưởng nghiệp vụ.

## Quyết định
- `AiClient` interface trong `integration/`; adapter theo provider. Service nghiệp vụ không import SDK provider.
- Data minimization: input tối thiểu, bí danh ("Trẻ A"), không tên/ngày sinh/ảnh; dị ứng dạng tổng hợp.
- Ghi model/provider và phiên bản prompt theo cấu hình; không ghi dữ liệu cá nhân trẻ trong request log.
- Lỗi/timeout/sai định dạng ⇒ `SERVICE_UNAVAILABLE`/`GATEWAY_TIMEOUT`, giữ dữ liệu người dùng; luồng không AI vẫn chạy.
- Backend kiểm tra dị ứng, dữ liệu bắt buộc, ngân sách bằng logic xác định — không chỉ dựa vào prompt.

## Tiêu chí chọn provider (khi chốt P-02)
Chính sách lưu/huấn luyện trên dữ liệu gửi lên · chi phí · độ trễ · chất lượng tiếng Việt · khả năng chạy local trên hạ tầng nhóm.

## Hệ quả
Thêm tác vụ AI = thêm method `AiClient` + prompt template + validate output; không đổi service nghiệp vụ khác.
