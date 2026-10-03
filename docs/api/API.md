# CareNest API — Auth, tài khoản, trẻ, người đón, ảnh

Tài liệu cho branch `feature/auth-user-management`. Base URL: `http://localhost:8080` + prefix `API_PREFIX`
(mặc định `/api`). Swagger UI: `http://localhost:8080/swagger-ui/index.html`.

Mục lục: [Tổng quan](#1-tổng-quan) · [Role & phân quyền](#2-role-và-phân-quyền) · [Xác thực](#3-xác-thực-và-token) ·
[Response & mã lỗi](#4-response-và-mã-lỗi) · [Auth](#5-auth) · [Tài khoản của tôi](#6-tài-khoản-của-tôi) ·
[Quản lý tài khoản](#7-quản-lý-tài-khoản) · [Quản lý trẻ](#8-quản-lý-trẻ) · [Người đón trẻ](#9-người-đón-trẻ) ·
[Phụ huynh](#10-phụ-huynh) · [Giáo viên](#11-giáo-viên) · [Quy tắc dữ liệu](#12-quy-tắc-dữ-liệu) ·
[Luồng nghiệp vụ](#13-luồng-nghiệp-vụ) · [Cấu hình & chạy](#14-cấu-hình-và-chạy) · [Test](#15-test) ·
[Ghi chú cho nhóm](#ghi-chú-cho-nhóm)

## 1. Tổng quan

| Nhóm | Nội dung |
| --- | --- |
| Đăng nhập | Nhân sự đăng nhập bằng **email**, phụ huynh bằng **số điện thoại**. JWT HS512, refresh token rotation, logout thu hồi token. |
| Mật khẩu | Bắt đổi mật khẩu lần đầu (`mustChangePassword`), tự đổi mật khẩu, quên mật khẩu qua OTP email, BGH reset mật khẩu. |
| Tài khoản | Tạo/xem/sửa/đổi role/khoá/xoá (chỉ tài khoản tạo nhầm). Không xoá tài khoản đã có dữ liệu, chỉ khoá. |
| Trẻ | BGH thêm trẻ bằng SĐT phụ huynh (tự tạo hoặc gắn tài khoản phụ huynh), sửa, chuyển phụ huynh. |
| Người đón | Phụ huynh thêm/sửa/xoá, BGH duyệt/từ chối, giáo viên chỉ thấy người đã duyệt. |
| Ảnh | Avatar + nhiều ảnh cho mọi user, ảnh trẻ, ảnh người đón. Lưu S3-compatible, trả presigned URL 15 phút. |

## 2. Role và phân quyền

| Role | Tên | Đăng nhập bằng | Ghi chú |
| --- | --- | --- | --- |
| `PRINCIPAL` | Hiệu trưởng | Email | Toàn quyền. Tạo sẵn khi khởi động (`ADMIN_EMAIL`/`ADMIN_PASSWORD`). |
| `VICE_PRINCIPAL` | Phó hiệu trưởng | Email | Chỉ Hiệu trưởng tạo. Quản lý giáo viên, nhân viên, phụ huynh, trẻ. |
| `HEAD_TEACHER` | Tổ trưởng | Email | Luôn đi kèm `TEACHER`. |
| `TEACHER` | Giáo viên | Email | |
| `STAFF` | Nhân viên | Email | Có `position` tuỳ chọn (kế toán, y tế, bảo vệ...). |
| `PARENT` | Phụ huynh | SĐT | Tạo tự động khi BGH thêm trẻ. |

- Một user có thể giữ nhiều role (vd. giáo viên đồng thời là phụ huynh).
- Role **không kế thừa**: mỗi endpoint liệt kê role được phép. "BGH" trong tài liệu = `PRINCIPAL` + `VICE_PRINCIPAL`.
- Phân cấp khi quản lý tài khoản:
  - Không ai thao tác được trên tài khoản Hiệu trưởng (trừ Hiệu trưởng tự sửa thông tin).
  - Phó HT không thao tác được trên Phó HT khác; chỉ Hiệu trưởng nâng/hạ Phó HT.
  - Không tự đổi role, tự khoá, tự xoá, tự reset mật khẩu.

## 3. Xác thực và token

| Client | Cách gửi | Cách nhận token |
| --- | --- | --- |
| Mobile | Header `X-Client-Type: mobile` khi login/refresh/logout; header `Authorization: Bearer <accessToken>` | Trong body |
| Web | Không gửi `X-Client-Type` | Cookie HttpOnly: `access_token` (Path=/), `refresh_token` (Path=`/api/auth`) |

- Access token 30 phút, refresh token 15 ngày (`JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION`).
- Payload JWT: `sub` (user id), `jti`, `iat`, `exp`, `type` (`access`/`refresh`), `authorities` (vd. `ROLE_TEACHER`,
  chỉ access token), `ver` (token version). Không chứa email/SĐT/mật khẩu.
- Refresh token rotation: mỗi lần refresh, token cũ bị đưa vào blacklist (`invalidated_tokens`); job chạy mỗi giờ xoá
  token đã hết hạn khỏi blacklist.
- Mỗi request kiểm tra trong DB:
  - Tài khoản bị khoá → **403 / 1102** ngay lập tức (không đợi token hết hạn).
  - Token cũ sau khi bị đổi role, đổi email/SĐT, chỉ định/bỏ tổ trưởng, BGH reset mật khẩu → **401 / 1104**
    (so `ver` trong token với `token_version` của user). FE chuyển về màn đăng nhập.
- `mustChangePassword = true` trong response login: FE phải chuyển tới màn đổi mật khẩu.

## 4. Response và mã lỗi

Các API trong tài liệu này trả:

```json
{ "code": 0, "message": "Đăng nhập thành công", "data": { } }
```

- `code = 0`: thành công. Khác 0: mã lỗi bên dưới; HTTP status đi kèm theo bảng.
- Lỗi validate (`code 1000`): `data` là map `{ "<tên trường>": "<thông báo>" }`.
- Request chưa khớp endpoint nào (sai URL, sai method, sai Content-Type, file vượt giới hạn multipart) do
  `GlobalExceptionHandler` xử lý, trả `{ "code": <HTTP status>, "desc": "...", "data": null }`.

| Code | HTTP | Ý nghĩa |
| --- | --- | --- |
| 0 | 200/201 | Thành công |
| 1000 | 400 | Dữ liệu không hợp lệ (validate, thiếu file, sai kiểu tham số) |
| 1001 | 400 | Dữ liệu sai định dạng (JSON hỏng, enum sai, ngày sai định dạng) |
| 1002 | 404 | Không tìm thấy tài nguyên |
| 1003 | 405 | Phương thức không được hỗ trợ |
| 1004 | 409 | Dữ liệu bị trùng hoặc vi phạm ràng buộc |
| 1005 | 415 | Kiểu dữ liệu gửi lên không được hỗ trợ |
| 1100 | 401 | Chưa đăng nhập |
| 1101 | 401 | Sai tài khoản hoặc mật khẩu |
| 1102 | 403 | Tài khoản đã bị khoá |
| 1103 | 403 | Không có quyền |
| 1104 | 401 | Token không hợp lệ, hết hạn, đã thu hồi hoặc cũ |
| 1200 | 409 | Email đã được sử dụng |
| 1201 | 409 | SĐT đã được sử dụng |
| 1202 | 400 | Mật khẩu xác nhận không khớp |
| 1203 | 404 | Tài khoản không tồn tại |
| 1204 | 400 | Mật khẩu hiện tại không chính xác |
| 1205 | 400 | Mật khẩu mới trùng mật khẩu cũ |
| 1206 | 403 | Phụ huynh không thuộc quản lý của giáo viên |
| 1207 | 400 | Role không được phép tạo/gán (chỉ `VICE_PRINCIPAL`, `TEACHER`, `STAFF`) |
| 1208 | 404 | Giáo viên không tồn tại |
| 1209 | 400 | Không tự khoá/mở khoá mình |
| 1210 | 409 | Tài khoản đã có dữ liệu, không xoá được (hãy khoá) |
| 1211 | 400 | Tài khoản nhân sự phải có email |
| 1212 | 400 | Tài khoản phụ huynh phải có SĐT |
| 1213 | 400 | Chỉ đổi role cho tài khoản nhân sự |
| 1214 | 400 | Không thực hiện thao tác này trên tài khoản của mình |
| 1300 | 400 | OTP không chính xác |
| 1301 | 429 | OTP đã gửi, thử lại sau N giây |
| 1302 | 400 | OTP hết hạn |
| 1303 | 403 | Chưa xác minh OTP |
| 1304 | 501 | Chưa hỗ trợ OTP qua SMS |
| 1305 | 503 | Không gửi được email |
| 1400 | 400 | File không phải ảnh JPG/PNG/WebP |
| 1401 | 413 | Ảnh quá 5MB |
| 1402 | 503 | Không lưu được ảnh |
| 1403 | 404 | Ảnh không tồn tại |
| 1500 | 404 | Trẻ không tồn tại / không phải con mình |
| 1501 | 404 | Người đón không tồn tại |
| 9999 | 500 | Lỗi hệ thống |

Các object trả về:

```jsonc
// UserResponse
{ "id": 3, "email": "gv@gmail.com", "phoneNumber": "0977000001", "fullName": "Cô Lan",
  "position": "Giáo viên lớp Mầm", "avatarUrl": "https://...", "roles": ["TEACHER"],
  "status": "ACTIVE", "mustChangePassword": true }

// ChildResponse
{ "id": 1, "fullName": "Bé Na", "dateOfBirth": "2022-05-01", "photoUrl": "https://...",
  "parent": { "id": 5, "fullName": "Chị Hoa", "phoneNumber": "0912345678" },
  "parentAccountCreated": true }

// PickupPersonResponse
{ "id": 1, "childId": 1, "childName": "Bé Na", "fullName": "Bà Tư", "relationship": "Bà ngoại",
  "phoneNumber": "0933333333", "photoUrl": "https://...", "status": "PENDING",
  "rejectReason": null, "reviewedAt": null }

// ImageResponse
{ "id": 1, "title": "Chứng chỉ", "url": "https://...", "createdAt": "2026-10-03T09:00:00Z" }

// PageResponse<T>
{ "items": [ ], "page": 0, "size": 20, "totalElements": 35, "totalPages": 2 }
```

URL ảnh là presigned URL hết hạn sau 15 phút: FE gọi lại API để lấy URL mới, không lưu lâu.

## 5. Auth

Không cần token (trừ `change-password`).

| Method | Endpoint | Body | Kết quả |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | `{"identifier":"admin@carenest.com","password":"Admin@123"}` | `SignInResponse` |
| POST | `/api/auth/refresh-token` | `{"refreshToken":"..."}` (web: cookie) | `SignInResponse` mới |
| POST | `/api/auth/logout` | `{"refreshToken":"..."}` (tuỳ chọn) | Thu hồi access + refresh, xoá cookie |
| POST | `/api/auth/change-password` | `{"oldPassword":"...","newPassword":"parent123","confirmPassword":"parent123"}` | Cần token; tắt `mustChangePassword` |
| POST | `/api/auth/forgot-password` | `{"identifier":"gv@gmail.com"}` | Gửi OTP 6 số qua email |
| POST | `/api/auth/verify-otp` | `{"identifier":"gv@gmail.com","otp":123456}` | Xác minh OTP |
| POST | `/api/auth/reset-password` | `{"identifier":"gv@gmail.com","password":"teacher456","confirmPassword":"teacher456"}` | Đặt mật khẩu mới |

```jsonc
// SignInResponse (mobile); web chỉ có expiresIn + mustChangePassword, token nằm trong cookie
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "tokenType": "Bearer",
  "expiresIn": 1800, "mustChangePassword": true }
```

Lỗi hay gặp: sai mật khẩu/không tồn tại `401/1101` (không tiết lộ tài khoản có tồn tại), bị khoá `403/1102`,
refresh token đã dùng/đã logout `401/1104`, quên mật khẩu bằng SĐT `501/1304`.

## 6. Tài khoản của tôi

Mọi role, dùng token của chính mình.

| Method | Endpoint | Body | Ghi chú |
| --- | --- | --- | --- |
| GET | `/api/users/me` | | `UserResponse` |
| PUT | `/api/users/me` | `{"fullName":"Lan Anh"}` | Chỉ sửa họ tên; email/SĐT do BGH sửa |
| PUT | `/api/users/me/avatar` | form-data `file` | Ảnh đại diện |
| GET | `/api/users/me/images` | | Danh sách `ImageResponse` |
| POST | `/api/users/me/images` | form-data `file`, `title` (tuỳ chọn) | Ảnh cá nhân (chứng chỉ, CCCD...) |
| DELETE | `/api/users/me/images/{imageId}` | | Ảnh người khác → `404/1403` |

## 7. Quản lý tài khoản

Role: `PRINCIPAL`, `VICE_PRINCIPAL`.

| Method | Endpoint | Body | Ghi chú |
| --- | --- | --- | --- |
| POST | `/api/management/staff` | `{"email":"gv@gmail.com","fullName":"Cô Lan","phoneNumber":"0977000001","password":"teacher123","role":"TEACHER","position":"Giáo viên lớp Mầm"}` | `role`: `VICE_PRINCIPAL` (chỉ HT), `TEACHER`, `STAFF`. Mật khẩu tạm, `mustChangePassword = true` |
| GET | `/api/management/users?role=&status=&keyword=&page=0&size=20` | | `PageResponse<UserResponse>`; `keyword` tìm trong họ tên, email, SĐT (chứa, không phân biệt hoa thường); `size` tối đa 100 |
| GET | `/api/management/users/{id}` | | `{ account, createdAt, lastLoginAt, children }` |
| PUT | `/api/management/users/{id}` | `{"fullName":"...","email":"...","phoneNumber":"...","position":"..."}` | Ghi đè toàn bộ; email/SĐT rỗng = xoá. Đổi email/SĐT → đăng xuất user |
| PUT | `/api/management/users/{id}/role` | `{"role":"STAFF"}` | Giữ `PARENT` nếu có; rời `TEACHER` thì mất `HEAD_TEACHER`; đăng xuất user |
| PUT | `/api/management/teachers/{id}/head-teacher` | | Thêm `HEAD_TEACHER`; đăng xuất user |
| DELETE | `/api/management/teachers/{id}/head-teacher` | | Bỏ `HEAD_TEACHER`; đăng xuất user |
| POST | `/api/management/users/{id}/reset-password` | `{"password":"temp1234"}` | Mật khẩu tạm + `mustChangePassword`; đăng xuất user |
| PUT | `/api/management/users/{id}/status` | `{"status":"LOCKED"}` / `{"status":"ACTIVE"}` | Khoá có hiệu lực ngay request tiếp theo |
| DELETE | `/api/management/users/{id}` | | Chỉ khi chưa đăng nhập lần nào, không có con, chưa thêm trẻ, chưa duyệt người đón; còn lại `409/1210` |
| GET | `/api/management/users/{id}/images` | | Ảnh cá nhân của user |
| POST | `/api/management/parents/{id}/reset-password` | | Mật khẩu phụ huynh về lại SĐT |

## 8. Quản lý trẻ

Role: `PRINCIPAL`, `VICE_PRINCIPAL`.

| Method | Endpoint | Body | Ghi chú |
| --- | --- | --- | --- |
| POST | `/api/management/children` | `{"childName":"Bé Na","dateOfBirth":"2022-05-01","parentPhone":"0912345678","parentName":"Chị Hoa"}` | SĐT mới → tạo tài khoản phụ huynh (mật khẩu = SĐT, `parentAccountCreated: true`); SĐT đã có → gắn vào tài khoản đó |
| GET | `/api/management/children?keyword=&page=0&size=20` | | `keyword` tìm theo tên trẻ, tên/SĐT phụ huynh |
| GET | `/api/management/children/{id}` | | `{ child, createdAt, pickupPersons }` (mọi trạng thái) |
| PUT | `/api/management/children/{id}` | `{"childName":"Bé Na Na","dateOfBirth":"2022-06-01"}` | |
| PUT | `/api/management/children/{id}/parent` | `{"parentPhone":"0988888888","parentName":"Anh Tùng"}` | Chuyển sang phụ huynh khác (nhập nhầm SĐT); người đón của trẻ về `PENDING` |
| PUT | `/api/management/children/{id}/photo` | form-data `file` | |

## 9. Người đón trẻ

| Method | Endpoint | Role | Body | Ghi chú |
| --- | --- | --- | --- | --- |
| GET | `/api/management/pickup-persons?status=PENDING` | BGH | | `status`: `PENDING` (mặc định), `APPROVED`, `REJECTED` |
| POST | `/api/management/pickup-persons/{id}/approve` | BGH | | → `APPROVED` |
| POST | `/api/management/pickup-persons/{id}/reject` | BGH | `{"reason":"Ảnh không rõ mặt"}` (tuỳ chọn) | → `REJECTED` |

Phụ huynh thêm/sửa/xoá ở [mục 10](#10-phụ-huynh); giáo viên xem ở [mục 11](#11-giáo-viên).

## 10. Phụ huynh

Role: `PARENT`. Chỉ thao tác trên con của mình (trẻ khác → `404/1500`).

| Method | Endpoint | Body | Ghi chú |
| --- | --- | --- | --- |
| GET | `/api/parent/children` | | Danh sách con |
| PUT | `/api/parent/children/{childId}/photo` | form-data `file` | |
| GET | `/api/parent/children/{childId}/pickup-persons` | | Mọi trạng thái |
| POST | `/api/parent/children/{childId}/pickup-persons` | form-data `fullName`, `relationship`, `phoneNumber`, `photo` (bắt buộc) | → `PENDING` |
| PUT | `/api/parent/pickup-persons/{id}` | form-data như trên, `photo` tuỳ chọn | Sửa xong quay về `PENDING` |
| DELETE | `/api/parent/pickup-persons/{id}` | | |

Upload phải là `multipart/form-data`; gửi `x-www-form-urlencoded` → `415`.

## 11. Giáo viên

Role: `TEACHER`.

| Method | Endpoint | Ghi chú |
| --- | --- | --- |
| GET | `/api/teacher/children/{childId}/pickup-persons` | Chỉ người đón `APPROVED`. Chưa có lớp học nên xem được mọi trẻ |
| POST | `/api/teacher/parents/{parentId}/reset-password` | Chỉ phụ huynh của trẻ do giáo viên thêm; hiện trẻ do BGH thêm nên trả `403/1206` (chờ entity Lớp) |

## 12. Quy tắc dữ liệu

| Trường | Quy tắc | Hợp lệ | Không hợp lệ |
| --- | --- | --- | --- |
| SĐT | `^(0\|\+84)\d{9}$`, `+84` chuẩn hoá về `0` | `0912345678`, `+84912345678` | `12345`, `091234567`, `84912345678` |
| Mật khẩu | 8–100 ký tự, có chữ và số | `teacher123` | `abc123`, `abcdefgh`, `12345678` |
| Email | Định dạng email, ≤ 100 ký tự, lưu chữ thường | `GV@Gmail.com` → `gv@gmail.com` | `abc`, `abc@` |
| Họ tên, tên trẻ, tên phụ huynh | Bắt buộc, ≤ 100 ký tự | `Cô Lan` | rỗng |
| `relationship` | Bắt buộc, ≤ 50 ký tự | `Bà ngoại` | rỗng |
| `position` | Tuỳ chọn, ≤ 100 ký tự | `Kế toán` | |
| `dateOfBirth` | `yyyy-MM-dd`, ngày quá khứ | `2022-05-01` | `2999-01-01` (`1000`), `01/05/2022` (`1001`) |
| OTP | Số 6 chữ số | `123456` | `12345` |
| Ảnh | JPG/PNG/WebP (kiểm tra nội dung file), ≤ 5MB | file ảnh thật | file text đổi đuôi `.png` → `1400` |
| Enum | Viết hoa đúng tên | `TEACHER`, `LOCKED` | `teacher` → `1001` (body) / `1000` (query) |

## 13. Luồng nghiệp vụ

**Tạo tài khoản và đăng nhập lần đầu**

1. Hiệu trưởng đăng nhập bằng tài khoản tạo sẵn, đổi mật khẩu.
2. Hiệu trưởng tạo Phó HT; HT/Phó HT tạo giáo viên, nhân viên với mật khẩu tạm.
3. Nhân sự đăng nhập bằng email, `mustChangePassword = true` → đổi mật khẩu.

**Thêm trẻ và phụ huynh**

1. BGH thêm trẻ với SĐT phụ huynh → hệ thống tạo tài khoản phụ huynh (mật khẩu = SĐT) hoặc gắn vào tài khoản có sẵn
   (anh chị em dùng chung tài khoản).
2. Phụ huynh đăng nhập bằng SĐT, đổi mật khẩu.
3. Quên mật khẩu: phụ huynh nhờ BGH reset về SĐT (chưa có OTP qua SMS).

**Người đón trẻ**

1. Phụ huynh thêm người đón kèm ảnh → `PENDING`.
2. BGH duyệt (`APPROVED`) hoặc từ chối có lý do (`REJECTED`).
3. Giáo viên chỉ thấy người đón `APPROVED` để đối chiếu khi trả trẻ.
4. Phụ huynh sửa người đón → quay về `PENDING`, phải duyệt lại.

**Nghỉ việc / tạo nhầm**

- Nhân sự nghỉ việc: khoá tài khoản (`LOCKED`), dữ liệu giữ nguyên, mở khoá được.
- Tạo nhầm (chưa đăng nhập, chưa có dữ liệu): xoá hẳn.

## 14. Cấu hình và chạy

```bash
cp .env.example .env                 # điền JWT_SECRET (>= 64 ký tự), POSTGRES_PASSWORD, MAIL_*
docker compose up -d postgres s3     # PostgreSQL + object storage (SeaweedFS, cổng 9000)
./mvnw spring-boot:run               # Windows: mvnw.cmd spring-boot:run
```

Hoặc chạy cả API trong Docker: `docker compose up --build`.

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `JWT_SECRET` | (bắt buộc) | Khoá ký JWT, ≥ 64 ký tự |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@carenest.com` / `Admin@123` | Tài khoản Hiệu trưởng tạo lúc khởi động |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | rỗng | SMTP Gmail (App Password) để gửi OTP |
| `STORAGE_ENDPOINT` | `http://localhost:9000` | S3-compatible endpoint; AWS S3 thì để trống |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | `carenest` / `carenest-secret` | Phải khớp `docker/s3.json` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Origin của web |
| `AUTH_COOKIE_SECURE` / `AUTH_COOKIE_SAME_SITE` | `false` / `Lax` | Production HTTPS: `true`; khác domain: `None` |
| `JPA_DDL_AUTO` | `update` | Tạm thời, đến khi có migration Flyway |

Chạy trên máy không cần biến môi trường: tạo `src/main/resources/application-local.yml` (gitignore), xem README.

## 15. Test

```bash
./mvnw test
```

| Test | Nội dung |
| --- | --- |
| `AuthFlowIntegrationTests` | Login email/SĐT, cookie web, refresh rotation, logout, quên mật khẩu, khoá tài khoản |
| `UserManagementIntegrationTests` | Danh sách/sửa/đổi role/reset/xoá tài khoản, quản lý trẻ, đổi phụ huynh |
| `ImageFlowIntegrationTests` | Avatar, ảnh user, ảnh trẻ, duyệt người đón |
| `StorageServiceTests` | Kiểm tra file ảnh theo nội dung |
| `ResponseContractTest`, `CareNestApplicationTests` | Test scaffold (hợp đồng `ResponseJson`, khởi động với PostgreSQL thật qua Testcontainers) |

Postman (`postman/`):

- `CareNest.postman_environment.json`: biến môi trường (`baseUrl`, tài khoản, token, id).
- `CareNest_Teacher_CRUD.postman_collection.json`: CRUD giáo viên chạy tay, token/id tự lưu.
- `CareNest.postman_collection.json`: 136 test case có assertion, chạy bằng Runner (Settings → Working directory =
  thư mục `postman/` để tìm `sample.png`).
- `CareNest_TestCases.xlsx`: tài liệu test case. Sinh lại bằng `python postman/generate_test_cases.py`.

## Ghi chú cho nhóm

Những điểm branch này lệch so với `docs/backend-coding-guide.md`, cần nhóm chốt trước khi merge:

1. **Spring Security + JWT**: guide ghi chưa chốt phương án auth; branch này hiện thực JWT HS512 + refresh token
   rotation + blacklist + `token_version`.
2. **Format response**: các API ở đây dùng `{code, message, data}` với mã nghiệp vụ (`AppExceptionHandler`, chỉ áp
   dụng cho `AuthController`, `ManagementController`, `ParentController`, `TeacherController`, `UserController`).
   Scaffold (`ResponseJson`/`ApiCode`/`GlobalExceptionHandler`) giữ nguyên cho phần còn lại. Nếu chốt dùng
   `ResponseJson`, cần chuyển các controller trên và báo FE/APP.
3. **Schema**: chưa có migration Flyway cho các bảng `users`, `user_roles`, `children`, `pickup_persons`,
   `user_images`, `invalidated_tokens`, `forgotpassword`; tạm dùng `ddl-auto: update`.
4. **Dependency mới**: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`,
   `spring-boot-starter-mail`, AWS SDK `s3` (BOM 2.55.11), `h2` (test), `spring-boot-starter-security-test` (test).
5. **compose.yaml**: thêm service `s3` (SeaweedFS, vì MinIO ngừng phát hành Docker image) và biến môi trường cho `api`.
6. **ResponseContractTest**: giới hạn `@WebMvcTest` vào controller test và tắt filter security, vì slice mặc định nạp
   mọi controller (cần service thật) và Spring Security chặn request chưa đăng nhập.
7. Comment trong code phần lớn đang là tiếng Anh; guide yêu cầu tiếng Việt.
