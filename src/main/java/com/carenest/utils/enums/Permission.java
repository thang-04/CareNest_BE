package com.carenest.utils.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Danh mục quyền (ADR-0017). Chỉ khai quyền BE thực sự kiểm tra bằng {@code @permissionGuard}; module mới tự thêm
 * quyền của nó. Quyền chỉ trả lời "được làm hay không" — phạm vi campus/lớp/trẻ vẫn do policy kiểm (AUTH-13).
 *
 * <p>{@code defaultRoles}: role có quyền mặc định (role cố định lấy thẳng từ đây, role cấu hình được seed vào
 * {@code role_permissions}). {@code configurableRoles}: role mà HT được bật/tắt hoặc tạo ngoại lệ.
 */
public enum Permission {
    CHILD_PROFILE_VIEW(
            "child",
            "Xem hồ sơ trẻ trong phạm vi",
            EnumSet.of(CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER, CareNestRole.PARENT),
            // Bếp không xem hồ sơ trẻ (AUTH-05)
            EnumSet.of(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER)),
    // AUTH-06: HT cấp tài khoản nghiệp vụ; PHT chỉ khi HT bật, và chỉ GV/bếp trong campus mình (StaffAccessPolicy)
    ACCOUNT_MANAGE(
            "account",
            "Cấp tài khoản, cấp lại mật khẩu tạm",
            EnumSet.of(CareNestRole.ADMIN, CareNestRole.PRINCIPAL),
            EnumSet.of(CareNestRole.VICE_PRINCIPAL)),
    ACCOUNT_STATUS_MANAGE(
            "account",
            "Khóa / mở tài khoản",
            EnumSet.of(CareNestRole.ADMIN, CareNestRole.PRINCIPAL),
            EnumSet.of(CareNestRole.VICE_PRINCIPAL)),
    // AUTH-16: PHT mặc định phân công GV/bếp trong campus mình
    STAFF_ASSIGNMENT_MANAGE(
            "staff",
            "Phân công nhân sự (campus, lớp)",
            EnumSet.of(CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL),
            EnumSet.of(CareNestRole.VICE_PRINCIPAL)),
    // AUTH-18: PHT mặc định cấp tài khoản phụ huynh cho trẻ đang học trong campus mình
    PARENT_ACCOUNT_MANAGE(
            "child",
            "Cấp, khóa/mở tài khoản phụ huynh và liên kết với trẻ",
            EnumSet.of(CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL),
            EnumSet.of(CareNestRole.VICE_PRINCIPAL)),
    // Không giao được cho ai: nếu giao, người được giao tự cấp thêm quyền cho mình (AUTH-11)
    PERMISSION_MANAGE(
            "account", "Quản lý phân quyền", EnumSet.of(CareNestRole.PRINCIPAL), EnumSet.noneOf(CareNestRole.class));

    /** Role có hàng trong ma trận; quyền các role khác cố định trong code (AUTH-11). */
    public static final Set<CareNestRole> CONFIGURABLE_ROLES = Collections.unmodifiableSet(
            EnumSet.of(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF));

    private final String module;
    private final String description;
    private final Set<CareNestRole> defaultRoles;
    private final Set<CareNestRole> configurableRoles;

    Permission(String module, String description, Set<CareNestRole> defaultRoles, Set<CareNestRole> configurableRoles) {
        this.module = module;
        this.description = description;
        this.defaultRoles = Collections.unmodifiableSet(defaultRoles);
        this.configurableRoles = Collections.unmodifiableSet(configurableRoles);
    }

    public String module() {
        return module;
    }

    public String description() {
        return description;
    }

    public Set<CareNestRole> defaultRoles() {
        return defaultRoles;
    }

    public Set<CareNestRole> configurableRoles() {
        return configurableRoles;
    }

    public boolean isConfigurableFor(CareNestRole role) {
        return configurableRoles.contains(role);
    }

    public static boolean isConfigurableRole(CareNestRole role) {
        return CONFIGURABLE_ROLES.contains(role);
    }

    /** Quyền cố định của role không cấu hình được (ADMIN, PRINCIPAL, PARENT); role cấu hình được ⇒ rỗng. */
    public static Set<Permission> fixedPermissionsOf(CareNestRole role) {
        if (isConfigurableRole(role)) {
            return EnumSet.noneOf(Permission.class);
        }
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission permission : values()) {
            if (permission.defaultRoles.contains(role)) {
                result.add(permission);
            }
        }
        return result;
    }

    /** Quyền HT được bật/tắt cho role; role cố định ⇒ rỗng. */
    public static Set<Permission> configurablePermissionsOf(CareNestRole role) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Permission permission : values()) {
            if (permission.isConfigurableFor(role)) {
                result.add(permission);
            }
        }
        return result;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.name().equals(code))
                .findFirst();
    }
}
