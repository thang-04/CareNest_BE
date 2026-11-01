package com.carenest.repository.account;

import com.carenest.entity.account.UserAccount;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.Collection;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Điều kiện lọc danh sách tài khoản; điều kiện phạm vi (userIds, roles) do service truyền vào. */
public final class UserAccountSpecifications {

    private UserAccountSpecifications() {}

    public static Specification<UserAccount> hasRole(CareNestRole role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("primaryRole"), role);
    }

    public static Specification<UserAccount> roleIn(Collection<CareNestRole> roles) {
        return (root, query, cb) ->
                roles == null ? null : root.get("primaryRole").in(roles);
    }

    public static Specification<UserAccount> hasStatus(UserStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** userIds null = không giới hạn; rỗng = không ai. */
    public static Specification<UserAccount> idIn(Collection<Long> userIds) {
        return (root, query, cb) -> {
            if (userIds == null) {
                return null;
            }
            return userIds.isEmpty() ? cb.disjunction() : root.get("id").in(userIds);
        };
    }

    /** Tìm theo username hoặc họ tên, không phân biệt hoa thường. */
    public static Specification<UserAccount> matches(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("username")), pattern), cb.like(cb.lower(root.get("fullName")), pattern));
        };
    }
}
