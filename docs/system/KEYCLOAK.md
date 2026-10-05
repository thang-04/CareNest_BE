# Keycloak — hướng dẫn sử dụng và cấu hình

Quyết định kiến trúc: ADR-0011. Contract cho FE/APP: `docs/contracts/AUTH_CONTRACT.md`. File này dành cho dev: chạy Keycloak local, hiểu cấu hình, lấy token, xử lý lỗi.

> Đã kiểm trên **Keycloak 26.8.0** (`compose.yaml`). Không ghi mật khẩu/secret thật vào file này; mọi giá trị nhạy cảm nằm trong `.env` (không commit).

## 1. Khái niệm cần nắm

| Khái niệm | Trong CareNest |
| --- | --- |
| Realm | Không gian user/role/client riêng. CareNest dùng **một** realm `carenest`. Realm `master` chỉ để quản trị Keycloak — không tạo user CareNest ở đó |
| Realm role | Role toàn cục: `ADMIN`, `PRINCIPAL`, `VICE_PRINCIPAL`, `TEACHER`, `KITCHEN_STAFF`, `PARENT`. Phạm vi lớp/campus/con nằm trong DB CareNest, **không** làm thành role |
| Client | Ứng dụng xin token: `carenest-web`, `carenest-mobile` (public + PKCE), `carenest-backend` (confidential, service account gọi Admin API), `carenest-dev` (**chỉ dev**, lấy token bằng curl) |
| Audience mapper | Thêm `carenest-api` vào claim `aud` của access token. API từ chối token không có `aud` này (vd. ID token, token của client khác) |
| User ID (`sub`) | UUID của user trong Keycloak = `user_accounts.keycloak_user_id` ở CareNest |
| Access token | JWT RS256, sống 5 phút, gửi trong header `Authorization: Bearer` |
| Refresh token | Client đổi lấy access token mới; mỗi refresh token chỉ dùng 1 lần. **CareNest không lưu** |
| Required action | Việc user phải làm ở lần đăng nhập tới, vd. `UPDATE_PASSWORD` |

API kiểm token **tại chỗ** bằng public key (JWKS) của Keycloak, có cache — không gọi Keycloak ở mỗi request. Keycloak tắt: không đăng nhập/refresh được, nhưng token còn hạn vẫn dùng được.

## 2. Chạy Keycloak local

1. Copy `.env.example` → `.env` (bắt buộc — compose báo lỗi nếu thiếu `KC_BOOTSTRAP_ADMIN_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_CLIENT_SECRET`). Đổi các giá trị `change-me`; `KEYCLOAK_ADMIN_CLIENT_SECRET` là chuỗi ngẫu nhiên ≥ 32 ký tự.
2. Chạy:

   ```bash
   docker compose up --build
   ```

   Hoặc chỉ hạ tầng, rồi chạy API bằng `mvnw.cmd spring-boot:run`:

   ```bash
   docker compose up -d postgres keycloak mailpit
   ```

3. Lần đầu: postgres chạy `docker/postgres/init/01-create-keycloak-db.sh` (tạo DB + user `keycloak`); Keycloak chạy `start-dev --import-realm`, import `docker/keycloak/import/carenest-realm.json`. Log có dòng `Realm 'carenest' imported`. Keycloak khởi động mất ~20 giây.
4. Địa chỉ:

   | | URL |
   | --- | --- |
   | Admin Console | `http://localhost:8180/admin` — đăng nhập bằng `KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD` (realm `master`), rồi chọn realm `carenest` ở dropdown góc trên bên trái |
   | OIDC discovery | `http://localhost:8180/realms/carenest/.well-known/openid-configuration` |
   | Account Console (user tự đổi mật khẩu) | `http://localhost:8180/realms/carenest/account` |
   | Mailpit (hộp thư dev) | `http://localhost:8025` |
   | API / Swagger | `http://localhost:8080/api/...` / `http://localhost:8080/swagger-ui/index.html` |

5. Volume postgres đã có từ trước (init script không chạy, Keycloak báo lỗi kết nối DB `keycloak`) ⇒ tạo tay (thay mật khẩu bằng giá trị `KEYCLOAK_DB_PASSWORD` trong `.env`):

   ```bash
   docker compose exec postgres psql -U carenest -d postgres -c "CREATE ROLE keycloak LOGIN PASSWORD 'mat-khau-trong-env';" -c "CREATE DATABASE keycloak OWNER keycloak;"
   ```

6. Biến môi trường của API:

   | Biến | API chạy trong compose | API chạy trên máy |
   | --- | --- | --- |
   | `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/carenest` | như bên trái |
   | `KEYCLOAK_JWK_SET_URI` | `http://keycloak:8080/realms/carenest/protocol/openid-connect/certs` | `http://localhost:8180/realms/carenest/protocol/openid-connect/certs` |
   | `KEYCLOAK_AUDIENCE` | `carenest-api` | như bên trái |
   | `CARENEST_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | như bên trái |

   `KC_HOSTNAME=http://localhost:8180` ép mọi token có `iss = http://localhost:8180/realms/carenest`, dù token lấy từ trình duyệt hay từ container. API trong container chỉ đổi địa chỉ **lấy key** (`keycloak:8080`), không đổi issuer.

## 3. Kiểm tra realm sau khi import (Admin Console, realm `carenest`)

- **Realm roles**: đủ 6 role ở mục 1 (ngoài ra có `offline_access`, `uma_authorization`, `default-roles-carenest` của Keycloak — API bỏ qua).
- **Clients**:
  - `carenest-web`: *Client authentication* Off · *Standard flow* On · *Direct access grants* Off · *Valid redirect URIs* `http://localhost:3000/*` · *Web origins* `http://localhost:3000` · tab *Advanced* → PKCE method `S256`.
  - `carenest-mobile`: như web, redirect `carenest://oauth/callback`, không có web origin.
  - `carenest-backend`: *Client authentication* On · chỉ bật *Service accounts roles* · tab *Service account roles* có `realm-management` → `manage-users`, `view-users`, `query-users`.
  - `carenest-dev`: public · *Direct access grants* On.
  - Audience: client → tab *Client scopes* → `<client>-dedicated` → mapper `carenest-api-audience` (loại *Audience*, *Included Custom Audience* = `carenest-api`, *Add to access token* On, *Add to ID token* Off). Có trên web, mobile, dev.
- **Realm settings**:
  - *Login*: User registration Off · Forgot password On · Login with email On · Edit username Off.
  - *Email*: host `mailpit`, port `1025`, from `noreply@carenest.local`.
  - *Tokens*: Access Token Lifespan 5 phút · Revoke Refresh Token On · Max reuse 0.
  - *Sessions*: SSO Session Idle 30 phút · SSO Session Max 10 giờ.
  - *Security defenses → Brute force detection*: Lockout temporarily, 5 lần sai.
  - *User profile*: `firstName` bắt buộc (lưu **họ tên đầy đủ**); `email`, `lastName` **không** bắt buộc; `email/firstName/lastName` chỉ admin sửa được.
- *Authentication → Policies → Password policy*: Minimum length 8 · Not username.

## 4. Cấu hình bằng tay trên Admin Console

Dùng để hiểu cấu hình, hoặc dựng môi trường không có file import. Ở dev nên sửa file JSON (mục 7) thay vì sửa tay.

1. **Tạo realm**: dropdown realm → *Create realm* → Realm name `carenest` → Create.
2. **Realm settings** — đặt các giá trị như mục 3 (tab *Login*, *Email*, *Tokens*, *Sessions*, *Security defenses*).
3. **User profile** (*Realm settings → User profile*):
   - `email` → Edit → tắt *Required field*; ở *Permission* bỏ chọn *User* trong "Who can edit" → Save.
   - `lastName`: tắt *Required field*, chỉ admin sửa.
   - `firstName`: giữ *Required field*, chỉ admin sửa.

   Lý do: mặc định Keycloak 26 bắt buộc `email` + `lastName`; user thiếu chúng bị chặn với lỗi `Account is not fully set up` (phụ huynh có thể không có email, tên tiếng Việt không tách họ/tên tin cậy). Xem `docs/knowledge/incidents/ENV-001-keycloak-user-profile-account-not-fully-set-up.md`.
4. **Password policy**: *Authentication → Policies → Password policy* → Add policy `Minimum length` = 8 và `Not username` → Save.
5. **Realm roles**: *Realm roles → Create role*, tạo lần lượt 6 role (viết hoa đúng như mục 1).
6. **Client `carenest-web`**: *Clients → Create client*:
   - *General settings*: Client type OpenID Connect · Client ID `carenest-web` → Next.
   - *Capability config*: Client authentication **Off** · Standard flow **On** · Direct access grants **Off** · Implicit **Off** → Next.
   - *Login settings*: Valid redirect URIs `http://localhost:3000/*` · Valid post logout redirect URIs `http://localhost:3000/*` · Web origins `http://localhost:3000` → Save.
   - Tab *Advanced* → *Advanced settings* → *Proof Key for Code Exchange Code Challenge Method* = `S256` → Save.
   - Audience: tab *Client scopes* → bấm `carenest-web-dedicated` → *Configure a new mapper* (hoặc *Add mapper → By configuration*) → **Audience** → Name `carenest-api-audience` · Included Custom Audience `carenest-api` · Add to ID token **Off** · Add to access token **On** → Save.
7. **Client `carenest-mobile`**: như web, redirect `carenest://oauth/callback` (APP chốt scheme sau), Web origins để trống, có audience mapper.
8. **Client `carenest-backend`**:
   - *Capability config*: Client authentication **On** · bỏ Standard flow và Direct access grants · bật **Service accounts roles**.
   - Tab *Credentials*: copy *Client secret* vào `.env` (`KEYCLOAK_ADMIN_CLIENT_SECRET`). Không commit, không gửi qua chat.
   - Tab *Service account roles* → *Assign role* → đổi bộ lọc sang *Filter by clients* → gõ `realm-management` → chọn `manage-users`, `view-users`, `query-users` → Assign. **Không** cần `view-realm`: backend lấy role qua `/users/{id}/role-mappings/realm/available`.
9. **Client `carenest-dev`** (chỉ dev): public · Standard flow On · Direct access grants **On** · redirect `http://localhost:8080/*` · có audience mapper.
10. **Tạo user thử**:
    - *Users → Add user*: Username (vd. `try.teacher`) · First name = họ tên đầy đủ · Email (tùy chọn) → Create.
    - Tab *Credentials* → *Set password* → Temporary **Off** nếu muốn lấy token ngay bằng curl (On ⇒ user phải đổi mật khẩu qua trình duyệt trước).
    - Tab *Role mapping* → *Assign role* → bộ lọc *Filter by realm roles* → chọn role.
    - ID ở đầu trang chi tiết user chính là `sub`. User tạo tay **chưa có** hồ sơ `user_accounts` ở CareNest ⇒ API nghiệp vụ trả 403 `Account is not provisioned`. Tài khoản thật phải tạo qua API CareNest (mục 6).

## 5. Lấy token và gọi API (dev)

Chỉ dev: password grant qua client `carenest-dev`. Web/Mobile thật dùng Authorization Code + PKCE, không gửi mật khẩu qua app.

PowerShell:

```powershell
$pw = "mat-khau-cua-user-dev"
$t = (Invoke-RestMethod -Method Post -Uri "http://localhost:8180/realms/carenest/protocol/openid-connect/token" -Body @{grant_type="password"; client_id="carenest-dev"; username="teacher.a"; password=$pw}).access_token
Invoke-RestMethod -Uri "http://localhost:8080/api/accounts/me" -Headers @{Authorization="Bearer $t"}
```

Git Bash (cần `jq`):

```bash
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/carenest/protocol/openid-connect/token" -d grant_type=password -d client_id=carenest-dev -d username=teacher.a -d "password=$DEV_PASSWORD" | jq -r .access_token)
```

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/accounts/me
```

User dev (`admin.dev`, `teacher.a`, `parent.a`, …) có từ khi seed dữ liệu scope; trước đó dùng user thử ở mục 4 bước 10. `/api/accounts/me` có từ khi làm CurrentUser; trước đó gọi một path bất kỳ dưới `/api`: có token hợp lệ trả 404, thiếu/sai token trả 401.

- **Xem claim**: payload JWT (phần giữa, base64url) phải có `iss = http://localhost:8180/realms/carenest`, `aud` chứa `carenest-api`, `sub`, `preferred_username`, `realm_access.roles`, `exp − iat = 300`. Chỉ dán token **dev** lên jwt.io, không bao giờ dán token môi trường thật.
- **Swagger**: Swagger UI → nút *Authorize* → dán access token (không gõ chữ `Bearer`) → gọi API. Token sống 5 phút; hết hạn thì lấy lại.
- **Refresh**: POST cùng endpoint token với `grant_type=refresh_token`, `client_id=carenest-dev`, `refresh_token=...`. Refresh token cũ hết hiệu lực sau khi dùng.
- **Logout**: POST `http://localhost:8180/realms/carenest/protocol/openid-connect/logout` với `client_id` + `refresh_token` ⇒ hủy session và refresh token. Access token đã cấp vẫn dùng được tới khi hết hạn (≤ 5 phút).

## 6. Cấp tài khoản qua CareNest

> Có từ phase "Cấp tài khoản" (ADR-0011). Mục này sẽ ghi endpoint, body và cách xem email trong Mailpit khi implement.

Nguyên tắc: tài khoản thật tạo qua API CareNest (Admin). CareNest tạo user Keycloak (họ tên ở `firstName`, mật khẩu tạm + `UPDATE_PASSWORD`), gán realm role, rồi tạo `user_accounts`; nếu bước sau lỗi thì xóa user Keycloak vừa tạo.

## 7. Sửa cấu hình realm và giữ trong git

- `docker/keycloak/import/carenest-realm.json` là **nguồn chuẩn** cấu hình realm ở dev. Muốn đổi thì sửa file này rồi kiểm lại trên console.
- Secret và giá trị theo môi trường dùng placeholder `${TEN_BIEN}` (Keycloak thay bằng biến môi trường của container lúc import): `KEYCLOAK_ADMIN_CLIENT_SECRET`, `CARENEST_WEB_ORIGIN`, `CARENEST_MOBILE_REDIRECT_URI`.
- Tránh thêm mảng `clientScopes` ở cấp realm trong file import: Keycloak chỉ tự tạo bộ client scope mặc định (`profile`, `email`, `roles`, `basic`…) khi file không khai báo mảng này, nên khai báo thiếu có thể làm token mất `sub`/role (chưa thử thực tế, tránh cho an toàn). Vì vậy audience mapper đặt trên từng client.
- `--import-realm` **bỏ qua realm đã tồn tại**. Muốn áp lại file ở dev, reset DB Keycloak (mất user tạo tay/tạo qua API; các `user_accounts` tương ứng thành mồ côi):

  ```bash
  docker compose stop keycloak
  ```

  ```bash
  docker compose exec postgres psql -U carenest -d postgres -c "DROP DATABASE keycloak WITH (FORCE);" -c "CREATE DATABASE keycloak OWNER keycloak;"
  ```

  ```bash
  docker compose up -d keycloak
  ```

- Đã chỉnh trên console và muốn đối chiếu: *Realm settings* → *Action* (góc phải) → *Partial export* (secret bị che, không có user) → so với file JSON, chép phần cần giữ vào.

## 8. Xử lý lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| API trả 401 dù token "trông đúng" | `iss` khác `KEYCLOAK_ISSUER_URI` (vd. token lấy qua IP LAN hoặc port khác) | Lấy token qua `http://localhost:8180`; test bằng điện thoại thật thì đặt `KEYCLOAK_HOSTNAME` = `http://<IP-LAN>:8180` và API dùng cùng issuer đó |
| 401, token không có `aud: carenest-api` | Client thiếu audience mapper | Thêm mapper (mục 4 bước 6), lấy token mới |
| `invalid_grant` / `Account is not fully set up` | User còn required action (đổi mật khẩu tạm) hoặc thiếu field bắt buộc của user profile | Đăng nhập qua trình duyệt một lần (Account Console); kiểm *User profile* như mục 3 — ENV-001 |
| `invalid_grant` / `Invalid user credentials` | Sai mật khẩu, hoặc bị khóa tạm sau 5 lần sai | Đợi hết thời gian khóa hoặc admin mở khóa (Users → user → tắt *Temporarily locked*) |
| 403 `Account is not provisioned` | `sub` chưa có trong `user_accounts` | Tạo tài khoản qua API CareNest hoặc kiểm seed dev |
| 403 sau khi vừa gán role | Token cũ chưa có role mới | Lấy token mới |
| `Invalid parameter: redirect_uri` | Redirect URI không khớp cấu hình client | Sửa *Valid redirect URIs* |
| Lỗi CORS trên web | Origin không có trong `CARENEST_CORS_ALLOWED_ORIGINS` (API) hoặc *Web origins* (Keycloak) | Thêm origin ở cả hai nơi |
| Keycloak không khởi động, lỗi kết nối DB | Chưa có DB `keycloak` (volume postgres cũ) | Tạo tay (mục 2 bước 5) |
| `required variable ... is missing a value` khi `docker compose up` | Thiếu `.env` | Copy `.env.example` → `.env` |
| Backend gọi Admin API bị 401/403 | Sai `KEYCLOAK_ADMIN_CLIENT_SECRET` hoặc thiếu service account role | Kiểm tab *Credentials* / *Service account roles* của `carenest-backend` |

## 9. Khi đưa lên server

Chưa chốt hạ tầng (`docs/system/DEPLOYMENT.md`). Khi làm:

- Chạy `start` (không `start-dev`) sau reverse proxy có HTTPS: `KC_HOSTNAME=https://<auth-domain>`, `KC_PROXY_HEADERS=xforwarded`, `KC_HEALTH_ENABLED=true`.
- Realm trên server **không có** client `carenest-dev` và user dev; redirect URI/web origin theo domain thật; secret, mật khẩu admin, mật khẩu DB sinh mới và để trong secret store.
- Tạo admin Keycloak cố định rồi xóa bootstrap admin. Hạn chế truy cập Admin Console (IP/VPN).
- Backup DB `keycloak` cùng lịch với DB `carenest`.
- API: `KEYCLOAK_ISSUER_URI=https://<auth-domain>/realms/carenest`, `KEYCLOAK_JWK_SET_URI` theo địa chỉ nội bộ API gọi được.
