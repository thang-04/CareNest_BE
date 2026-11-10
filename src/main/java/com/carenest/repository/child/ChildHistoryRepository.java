package com.carenest.repository.child;

import com.carenest.entity.child.ChildHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildHistoryRepository extends JpaRepository<ChildHistory, Long> {

    List<ChildHistory> findAllByChildIdOrderByCreatedAtAscIdAsc(Long childId);

    /** Họ tên người thao tác để hiện lịch sử (chỉ đọc bảng tài khoản, không qua feature account). */
    @Query(
            value = "SELECT id AS userId, full_name AS fullName FROM user_accounts WHERE id IN (:ids)",
            nativeQuery = true)
    List<UserName> findUserNames(@Param("ids") Collection<Long> ids);

    interface UserName {
        Long getUserId();

        String getFullName();
    }
}
