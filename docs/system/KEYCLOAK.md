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

   Lý do: mặc định Keycloak 26 bắt buộc `email` + `lastName`; user thiếu chúng bị chặn với lỗi `Account is not fully set up` (phụ huynh có thể không có email, tên tiếng Việt không tách họ/tên tin cậy). Xem `docs/knowledge/incidents/ENV-261004-keycloak-user-profile-account-not-fully-set-up.md`.
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

### User dev và dữ liệu giả

Compose mount `docker/keycloak/dev/carenest-users-0.json` (8 user, id cố định, đăng nhập bằng email `<user>@gmail.com` hoặc username, mật khẩu chung `demo123` lưu dạng hash nên không bị chính sách `length(8)` chặn lúc import) và API chạy profile `dev` nạp `db/dev/R__dev_seed.sql` (hồ sơ `user_accounts` cùng id, 2 campus, 2 lớp, 3 trẻ, phân công, liên kết). Chạy API ngoài compose thì bật profile: `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev`.

Keycloak đã có realm thì `--import-realm` bỏ qua file user dev: đổi file này ⇒ partial import `ifResourceExists=OVERWRITE` (`docs/knowledge/TROUBLESHOOTING.md`) hoặc tạo lại DB `keycloak`.

| User | Role | Phạm vi trong seed (năm học 2026-2027) |
| --- | --- | --- |
| `admin` | ADMIN | Không có phạm vi dữ liệu trẻ |
| `principal` | PRINCIPAL | Toàn trường |
| `viceprincipal` | VICE_PRINCIPAL | Cơ sở A |
| `teacher1` | TEACHER | Lớp A1 |
| `teacher2` | TEACHER | Lớp A1 tới 30/09/2026 (đã hết), Lớp B1 từ 01/10/2026 |
| `parent1` | PARENT | Trẻ Demo A1-01 |
| `parent2` | PARENT | Trẻ Demo B1-01 |
| `kitchen` | KITCHEN_STAFF | Bếp Cơ sở A |

Lớp A1 có `Trẻ Demo A1-01`, `Trẻ Demo A1-02`; lớp B1 có `Trẻ Demo B1-01`. Tất cả là dữ liệu giả.

Thử phân quyền với `GET /api/children/{id}` (id thật xem bằng `docker compose exec postgres psql -U carenest -d carenest -c "select id, full_name from children"`) — kết quả đã kiểm e2e:

| User | A1-01 | A1-02 | B1-01 | id không tồn tại |
| --- | --- | --- | --- | --- |
| `teacher1` | 200 | 200 | 403 | 403 |
| `teacher2` (A1 đã hết hạn) | 403 | 403 | 200 | 403 |
| `viceprincipal` | 200 | 200 | 403 | 403 |
| `principal` | 200 | 200 | 200 | 403 |
| `parent1` | 200 | 403 | 403 | 403 |
| `parent2` | 403 | 403 | 200 | 403 |
| `kitchen`, `admin` | 403 | 403 | 403 | 403 |

User tạo tay trên console (mục 4 bước 10) chưa có hồ sơ CareNest ⇒ `/api/accounts/me` trả 403 `Account is not provisioned`; thêm tay khi cần thử:

```bash
docker compose exec postgres psql -U carenest -d carenest -c "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('<ID user Keycloak>', 'try.teacher', 'Giáo viên thử');"
```

- **Xem claim**: payload JWT (phần giữa, base64url) phải có `iss = http://localhost:8180/realms/carenest`, `aud` chứa `carenest-api`, `sub`, `preferred_username`, `realm_access.roles`, `exp − iat = 300`. Chỉ dán token **dev** lên jwt.io, không bao giờ dán token môi trường thật.
- **Swagger**: Swagger UI → nút *Authorize* → dán access token (không gõ chữ `Bearer`) → gọi API. Token sống 5 phút; hết hạn thì lấy lại.
- **Refresh**: POST cùng endpoint token với `grant_type=refresh_token`, `client_id=carenest-dev`, `refresh_token=...`. Refresh token cũ hết hiệu lực sau khi dùng.
- **Logout**: POST `http://localhost:8180/realms/carenest/protocol/openid-connect/logout` với `client_id` + `refresh_token` ⇒ hủy session và refresh token. Access token đã cấp vẫn dùng được tới khi hết hạn (≤ 5 phút).

## 6. Cấp tài khoản qua CareNest

Tài khoản thật **luôn** tạo qua API CareNest (role ADMIN), không tạo tay trên console: một lệnh tạo cả user Keycloak lẫn hồ sơ `user_accounts`. Contract đầy đủ: `docs/contracts/AUTH_CONTRACT.md`.

1. Lấy token `admin` (mục 5).
2. Tạo tài khoản:

   ```powershell
   $body = @{ username="teacher.c"; fullName="Giáo viên C"; email="teacher.c@example.test"; role="TEACHER"; temporaryPassword="<mật khẩu tạm>" } | ConvertTo-Json
   Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/accounts" -Headers @{Authorization="Bearer $t"} -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
   ```

   - Role tạo được: PRINCIPAL, VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF, PARENT (ADMIN thì không).
   - `temporaryPassword` bắt buộc, trừ khi có `email` và `KEYCLOAK_ACTIONS_EMAIL_ENABLED=true` (compose bật sẵn vì có Mailpit).
   - BE tạo user Keycloak (họ tên ở `firstName`, required action `UPDATE_PASSWORD`), gán realm role, ghi `user_accounts`. Bước sau lỗi thì xóa user Keycloak vừa tạo (bù trừ). Xóa cũng lỗi thì log ERROR `compensationFailed|manualCleanupRequired` kèm `keycloakUserId` để dọn tay.
   - Có email: Keycloak gửi email "Update Your Account" — xem ở Mailpit `http://localhost:8025`. Response `setupEmailSent` cho biết đã gửi chưa.
3. Người dùng đăng nhập lần đầu qua trình duyệt (web/app hoặc Account Console) bằng mật khẩu tạm ⇒ Keycloak bắt đặt mật khẩu mới. Trước bước này, password grant trả `Account is not fully set up`.
4. Khóa / mở khóa:

   ```powershell
   Invoke-RestMethod -Method Patch -Uri "http://localhost:8080/api/accounts/<userId>/status" -Headers @{Authorization="Bearer $t"} -ContentType "application/json" -Body '{"status":"INACTIVE"}'
   ```

   `INACTIVE`: CareNest chặn ngay (access token còn hạn cũng nhận 403 `Account is inactive`), Keycloak disable user và hủy mọi session (refresh token hết dùng được, không đăng nhập lại được). `ACTIVE` mở lại. Bước Keycloak lỗi ⇒ 503 nhưng trạng thái CareNest đã lưu; gọi lại để đồng bộ.
5. Phân công lớp/campus hoặc liên kết phụ huynh–trẻ cho tài khoản mới: **chưa có API** (module organization/child, đợt sau). Dev dùng seed hoặc SQL.

Đổi secret của `carenest-backend` (tab *Credentials* → *Regenerate*) thì cập nhật `KEYCLOAK_ADMIN_CLIENT_SECRET` trong `.env` rồi khởi động lại API.

## 7. Sửa cấu hình realm và giữ trong git

- `docker/keycloak/import/carenest-realm.json` là **nguồn chuẩn** cấu hình realm ở dev. Muốn đổi thì sửa file này rồi kiểm lại trên console.
- User dev nằm riêng ở `docker/keycloak/dev/carenest-users-0.json` (cùng thư mục import với file realm trong container); server thật không mount file này. Đổi user dev thì sửa cả file này và `db/dev/R__dev_seed.sql` (cùng `id` ↔ `keycloak_user_id`).
- Client `carenest-dev` trong realm có `"enabled": "${CARENEST_DEV_CLIENT_ENABLED:false}"`: mặc định **tắt**, compose dev đặt `true`. SMTP lấy từ `CARENEST_SMTP_HOST`/`CARENEST_SMTP_PORT` (mặc định `mailpit:1025`). Placeholder dạng `${TEN_BIEN:mac_dinh}` đã kiểm chạy được trên KC 26.8.0.
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
| `invalid_grant` / `Account is not fully set up` | User còn required action (đổi mật khẩu tạm) hoặc thiếu field bắt buộc của user profile | Đăng nhập qua trình duyệt một lần (Account Console); kiểm *User profile* như mục 3 — ENV-261004-keycloak-user-profile-account-not-fully-set-up |
| `invalid_grant` / `Invalid user credentials` | Sai mật khẩu, hoặc bị khóa tạm sau 5 lần sai | Đợi hết thời gian khóa hoặc admin mở khóa (Users → user → tắt *Temporarily locked*) |
| 403 `Account is not provisioned` | `sub` chưa có trong `user_accounts` | Tạo tài khoản qua API CareNest hoặc kiểm seed dev |
| 403 sau khi vừa gán role | Token cũ chưa có role mới | Lấy token mới |
| `Invalid parameter: redirect_uri` | Redirect URI không khớp cấu hình client | Sửa *Valid redirect URIs* |
| Lỗi CORS trên web | Origin không có trong `CARENEST_CORS_ALLOWED_ORIGINS` (API) hoặc *Web origins* (Keycloak) | Thêm origin ở cả hai nơi |
| Keycloak không khởi động, lỗi kết nối DB | Chưa có DB `keycloak` (volume postgres cũ) | Tạo tay (mục 2 bước 5) |
| `required variable ... is missing a value` khi `docker compose up` | Thiếu `.env` | Copy `.env.example` → `.env` |
| Mọi API trả 503 `Authentication service unavailable` | API mới khởi động mà không lấy được JWKS (Keycloak tắt / sai `KEYCLOAK_JWK_SET_URI`) | Bật Keycloak, kiểm URL JWKS; token đã cấp dùng lại được ngay khi lấy được key |
| Tạo tài khoản trả 400 `Account data rejected by identity provider` | Mật khẩu tạm vi phạm policy (vd. trùng username, < 8 ký tự) hoặc họ tên có ký tự Keycloak cấm (`( ) < > & /`...) | Đổi mật khẩu tạm / bỏ ký tự đặc biệt khỏi họ tên |
| Password grant `invalid_client` với `carenest-dev` | Client dev đang tắt (`CARENEST_DEV_CLIENT_ENABLED` không phải `true` lúc import realm) | Đặt `true` trong `.env` rồi reset DB Keycloak (mục 7) |
| Tạo/khóa tài khoản trả 503 `Identity provider unavailable` | Thiếu/sai `KEYCLOAK_ADMIN_CLIENT_SECRET`, sai `KEYCLOAK_BASE_URL`, Keycloak tắt, hoặc thiếu service account role | Kiểm `.env`, log API (`keycloakStatus=...`), tab *Credentials* / *Service account roles* của `carenest-backend` |
| Tạo tài khoản trả 409 dù DB chưa có username | User đã có bên Keycloak (tạo tay trên console) | Xóa user đó trên console rồi tạo lại qua API |

## 9. Khi đưa lên server

Chưa chốt hạ tầng (`docs/system/DEPLOYMENT.md`). Khi làm:

- Chạy `start` (không `start-dev`) sau reverse proxy có HTTPS: `KC_HOSTNAME=https://<auth-domain>`, `KC_PROXY_HEADERS=xforwarded`, `KC_HEALTH_ENABLED=true`.
- Server: `CARENEST_DEV_CLIENT_ENABLED=false` (hoặc không đặt — mặc định tắt) và **không** mount file user dev; redirect URI/web origin theo domain thật; secret, mật khẩu admin, mật khẩu DB sinh mới và để trong secret store.
- Tạo admin Keycloak cố định rồi xóa bootstrap admin. Hạn chế truy cập Admin Console (IP/VPN).
- Backup DB `keycloak` cùng lịch với DB `carenest`.
- API: `KEYCLOAK_ISSUER_URI=https://<auth-domain>/realms/carenest`, `KEYCLOAK_JWK_SET_URI` theo địa chỉ nội bộ API gọi được.
