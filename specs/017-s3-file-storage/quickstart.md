# Quickstart: kiểm chứng 017 trên dev

## Điều kiện

- `CareNest_BE/.env` có `CARENEST_EDUCATION_MEDIA_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (đã có 12/11/2026).
- Bucket: Block public bật; CORS (localhost:5173, dev., web.); lifecycle `imports/` 7 ngày; IAM inline policy chỉ bucket (đã làm 12/11/2026).
- **Còn làm**: lifecycle thứ hai — Management → Create lifecycle rule → tên `xoa-tmp-1-ngay`, prefix `tmp/`, Expire current versions sau **1** ngày.
- Migration V122 đã được Tiền duyệt và chạy.

## Kiểm tự động

```bash
./mvnw -q -Dtest='FileStorageTest,FileTypesTest,StoragePropertiesTest' test      # tên tệp, giới hạn, MIME, key cũ/mới (S3 mock)
./mvnw -q -Dtest='LearningResource*Test,ParentPhoto*Test,ChildPhoto*Test,MealPhoto*Test,UserSignature*Test,Inspection*Test' test
./mvnw -q -Dtest=StorageS3Smoke test      # S3 thật: put/presign GET/presign PUT+confirm/copy/delete, dọn sạch prefix smoke
```

## Kiểm tay (Postman, user dev)

1. PUT `/api/accounts/me/photo` ảnh 1 MB ⇒ 200; trên S3 có `avatars/users/user_<id>_<thời gian>_<xxxx>.jpg`, ảnh cũ bị xóa.
2. Cùng API, ảnh 3 MB ⇒ 413, `FILE_TOO_LARGE` "…(tối đa 2 MB)". Tệp `.exe` đổi đuôi `.jpg` ⇒ 415, không có object mới.
3. GET `/api/accounts/me/photo/url` ⇒ mở `url` trên trình duyệt thấy ảnh; sau 15 phút mở lại ⇒ 403 `Request has expired`.
4. Mở `https://<bucket>.s3.<region>.amazonaws.com/<key>` không chữ ký ⇒ 403.
5. Học liệu: POST `upload-url` (mp4 80 MB) ⇒ PUT tệp lên `url` ⇒ POST `confirm` ⇒ học liệu xuất hiện; S3 có `hoclieu/...`, không còn `tmp/...`.
6. POST `upload-url` khai 150 MB ⇒ 413. PUT một tệp PDF với `uploadId` đuôi `.mp4` rồi `confirm` ⇒ 415, `tmp/...` bị xóa.
7. Tài khoản/ảnh có key UUID cũ vẫn xem được qua endpoint cũ và `.../url`.
