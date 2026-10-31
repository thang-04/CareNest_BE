package com.carenest.entity.account;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "user_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_user_id", nullable = false, updatable = false, length = 36)
    private String keycloakUserId;

    @Column(nullable = false, updatable = false, length = 100)
    private String username;

    @Column
    private String email;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    // CCCD phụ huynh, khóa nhận diện (AUTH-18, V10): UNIQUE; tài khoản cũ chưa có được bổ sung khi hồ sơ trẻ ghi CCCD
    @Column(name = "citizen_id", length = 12)
    private String citizenId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // Bản sao role Keycloak để lọc/kiểm phạm vi quản lý (V8); token vẫn là nguồn quyền
    @Enumerated(EnumType.STRING)
    // Không updatable: merge entity đọc trước đó không ghi đè role mới; đổi qua UserAccountRepository.updatePrimaryRole
    @Column(name = "primary_role", updatable = false, length = 20)
    private CareNestRole primaryRole;

    /** Ảnh chân dung (phụ huynh tải khi đăng nhập App lần đầu, GV đối chiếu khi trả trẻ — PCK-02); tệp ở S3. */
    @Column(name = "photo_key", length = 36)
    private String photoKey;

    @Column(name = "photo_content_type", length = 30)
    private String photoContentType;

    @Column(name = "photo_size_bytes")
    private Integer photoSizeBytes;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserAccount create(String keycloakUserId, String username, String email, String fullName) {
        return create(keycloakUserId, username, email, fullName, null);
    }

    public static UserAccount create(
            String keycloakUserId, String username, String email, String fullName, CareNestRole primaryRole) {
        UserAccount account = new UserAccount();
        account.keycloakUserId = keycloakUserId;
        account.username = username;
        account.email = email;
        account.fullName = fullName;
        account.status = UserStatus.ACTIVE;
        account.primaryRole = primaryRole;
        return account;
    }

    /** Tài khoản phụ huynh tạo khi tiếp nhận / kích hoạt (AUTH-18, AUTH-20): tên đăng nhập = SĐT, kèm CCCD. */
    public static UserAccount createParent(
            String keycloakUserId, String username, String email, String fullName, String citizenId) {
        UserAccount account = create(keycloakUserId, username, email, fullName, CareNestRole.PARENT);
        account.citizenId = citizenId;
        return account;
    }

    /** Tài khoản phụ huynh cũ chưa có CCCD: ghi CCCD từ hồ sơ trẻ (AUTH-18); đã có thì giữ. */
    public void assignCitizenIdIfMissing(String citizenId) {
        if (this.citizenId == null) {
            this.citizenId = citizenId;
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void changeStatus(UserStatus newStatus) {
        this.status = newStatus;
    }

    /** Phụ huynh được nhận làm nhân sự (chỉ bản trong bộ nhớ, DB ghi bằng updatePrimaryRole). */
    public void becomeStaff(CareNestRole staffRole) {
        this.primaryRole = staffRole;
    }

    /** Thu hồi vai trò nhân sự của người vẫn là phụ huynh (chỉ bản trong bộ nhớ, DB ghi bằng updatePrimaryRole). */
    public void becomeParentOnly() {
        this.primaryRole = CareNestRole.PARENT;
    }

    public boolean hasPhoto() {
        return photoKey != null;
    }

    /** Thay ảnh chân dung; trả mã tệp cũ để dọn sau khi lưu (null khi chưa có). */
    public String replacePhoto(String key, String contentType, int sizeBytes, Instant at) {
        String old = this.photoKey;
        this.photoKey = key;
        this.photoContentType = contentType;
        this.photoSizeBytes = sizeBytes;
        this.photoUpdatedAt = at;
        return old;
    }
}
