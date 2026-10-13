# ADR-0014 — Service interface + Impl; enum và hằng dùng chung tập trung ở `utils/`

- Status: ACCEPTED
- Date: 2026-10-13
- Liên quan: ADR-0001 (layered monolith), ADR-0007 (interface + adapter trong `integration/`), guide mục 3–6

## Bối cảnh
Guide v1 để service là class cụ thể (`public class ChildService`) và không quy định nơi đặt enum/hằng. Khi code nghiệp vụ tăng, enum nằm rải rác (`security/`, `entity/<feature>/`, lồng trong service/exception) và hằng hệ thống (tên cookie, thời hạn phiên) nằm trong controller/service. Team chốt thống nhất cấu trúc để mọi thành viên viết cùng một kiểu.

## Quyết định
1. **Service = interface + Impl.** `service/<feature>/XxxService` là interface (giữ tên gốc để controller, policy và test mock không đổi); implementation ở `service/<feature>/impl/XxxServiceImpl` mang `@Service`, `@Transactional`, `@Slf4j`, `@RequiredArgsConstructor`. Bean khác trong `service/` (vd. state store) theo cùng mẫu. Interface chứa method public được gọi từ ngoài và nested type (record/enum/exception) thuộc contract của nó.
2. **Repository** giữ là interface Spring Data (Spring sinh implementation). Chỉ tạo `XxxRepositoryCustom` + `XxxRepositoryImpl` khi cần query custom.
3. **`integration/`**: mọi client dịch vụ ngoài là interface trung lập + adapter theo provider (`IdentityProviderClient` ↔ `KeycloakIdentityProviderClient`).
4. **Enum dùng chung** (enum top-level, hoặc enum lồng được class khác tham chiếu) ⇒ mỗi enum 1 file trong `utils/enums/`. `ApiCode` giữ ở `utils/`.
5. **Hằng dùng chung** (dùng ≥2 class, hoặc giá trị hệ thống: tên cookie/header, thời hạn phiên/OTP, ngưỡng rate limit) ⇒ một file `utils/Constants.java` (`final`, constructor private), nhóm bằng nested `public static final class` (`Auth`, `Security`, ...). Hằng `private` chỉ một class dùng ⇒ giữ tại chỗ. Giá trị nghiệp vụ PENDING vẫn đưa vào cấu hình, không vào `Constants`.
6. Ngoài phạm vi: `security/` (`CurrentUserService`, `*AccessPolicy`), controller, mapper.
7. `ArchitectureRulesTest` tự kiểm: `@Service` trong `service/` nằm ở `..impl..` với hậu tố `Impl`; class top-level trong `service/` ngoài `impl/` là interface; enum top-level nằm trong `utils/`.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Giữ service là class cụ thể (guide v1) | Ít file; CGLIB proxy và Mockito vẫn chạy với class | Team muốn tách contract/implementation thống nhất; dễ lộ method nội bộ |
| **Interface + Impl, interface giữ tên gốc** (chọn) | Contract rõ; caller/mock không đổi khi refactor | Mỗi service 2 file |
| Interface `IXxxService` + `XxxService` | Tên impl ngắn | Đổi mọi caller; tiền tố `I` không phải idiom Java/Spring |
| Hằng chia nhiều file `utils/constants/*Constants` | File nhỏ theo nhóm | Team chọn 1 file `Constants` để dễ tìm |

## Hệ quả
- Service mới PHẢI có interface + `Impl`; test unit service đặt `src/test/.../service/<feature>/impl/XxxServiceImplTest` và khởi tạo `XxxServiceImpl`.
- Enum/hằng dùng chung mới PHẢI đặt ở `utils/enums/` hoặc `utils/Constants.java`; đổi package enum không đổi giá trị lưu DB (`@Enumerated(STRING)`) hay JSON.
- Code đã có trên nhánh khác phải refactor theo ADR này trước khi merge.

## Còn chờ
Không.
