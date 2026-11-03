---
id: ENV-261103-seaweedfs-aws-chunked-photo-corrupt
type: env
modules: [nutrition, integration]
rules: [NUT-16]
status: workaround
date: 2026-11-03
keywords: [SeaweedFS, S3, aws-chunked, chunk-signature, ảnh bữa, meal photo, StorageService, checksum]
similar_to: []
---

# ENV-261103-seaweedfs-aws-chunked-photo-corrupt — Ảnh tải về hỏng khi S3 local là SeaweedFS

## Symptom
Đăng ảnh bữa `POST /api/nutrition/meal-photos` trả 201, nhưng `GET .../meal-photos/{id}/content` trả tệp không mở được: Web `<img>` có `naturalWidth = 0`, byte đầu không phải `FF D8`. Đầu object:
```text
d42a;chunk-signature=18c73cf3e5992a8dc4ee4cbca8b238cec6e3c78f4aa37c71aee2f46002f88110
```

## Điều kiện tái hiện
Stack local, S3 là `chrislusf/seaweedfs:3.97` (`weed server -s3`), `CARENEST_EDUCATION_MEDIA_ENDPOINT=http://s3:8333`, path-style; AWS SDK v2 mặc định (checksum trailer). Dùng SeaweedFS vì `minio/minio` không còn pull được (`pull access denied`, quay.io 401).

## Attempts — đã thử (cập nhật mỗi phiên điều tra, để phiên sau không lặp lại)
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | So byte `content` với tệp gốc | Khác từ byte 1, object có khung `chunk-signature` | Lỗi ở lúc ghi S3, không phải FE |
| 2 | Đặt env api `AWS_REQUEST_CHECKSUM_CALCULATION=when_required`, `AWS_RESPONSE_CHECKSUM_VALIDATION=when_required`, đăng lại | Byte tải về trùng tệp gốc, Web hiện ảnh | Workaround chỉ cho stack local |

## Root cause
SeaweedFS 3.97 không giải mã body `aws-chunked` có checksum trailer mà AWS SDK v2 gửi mặc định, lưu nguyên khung chunk vào object.

## Fix
Không sửa code. Chỉ stack local dùng SeaweedFS đặt hai biến môi trường trên cho container api. S3 thật không cần.

## Regression test
Không có (lỗi môi trường); `KitchenRecordsIntegrationTest` thay `StorageService` bằng mock.

## Ảnh hưởng
Mọi upload qua `StorageService` (học liệu, ảnh bữa) trên S3 tương thích không hỗ trợ checksum trailer. Dữ liệu: ảnh đã đăng trước workaround phải đăng lại.

## Lesson
Thử S3 local thay MinIO ⇒ kiểm byte tải về, không chỉ status 201.
