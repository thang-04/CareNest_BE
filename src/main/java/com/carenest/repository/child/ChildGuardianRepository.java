package com.carenest.repository.child;

import com.carenest.entity.child.ChildGuardian;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildGuardianRepository extends JpaRepository<ChildGuardian, Long> {

    List<ChildGuardian> findAllByChildIdOrderByPositionAsc(Long childId);

    /** Phụ huynh trên mọi hồ sơ trẻ có SĐT hoặc CCCD trong danh sách (kiểm một người một tài khoản, AUTH-18). */
    @Query("select g from ChildGuardian g where g.phone in :phones or g.citizenId in :citizenIds order by g.id")
    List<ChildGuardian> findAllByPhoneOrCitizenId(
            @Param("phones") Collection<String> phones, @Param("citizenIds") Collection<String> citizenIds);

    /** Bản ghi phụ huynh của tài khoản (đã kích hoạt) hoặc cùng SĐT với tên đăng nhập (liên kết cũ), cũ nhất trước. */
    @Query("select g from ChildGuardian g where g.userId = :userId or g.phone = :phone order by g.id")
    List<ChildGuardian> findAllOfAccount(@Param("userId") Long userId, @Param("phone") String phone);

    /**
     * Trạng thái tài khoản và lần gửi SMS gần nhất của từng phụ huynh trên hồ sơ trẻ: chưa có liên kết với trẻ ⇒
     * {@code accountStatus} null (Chưa kích hoạt). Chỉ đọc bảng tài khoản, không qua feature account.
     */
    @Query(
            value = "SELECT g.id AS guardianId, u.status AS accountStatus, s.status AS smsStatus,"
                    + " CAST(EXTRACT(EPOCH FROM s.sent_at) * 1000 AS BIGINT) AS smsAtMillis"
                    + " FROM child_guardians g"
                    + " LEFT JOIN guardian_child_links l ON l.child_id = g.child_id AND l.user_id = g.user_id"
                    + " LEFT JOIN user_accounts u ON u.id = l.user_id"
                    + " LEFT JOIN LATERAL (SELECT p.status, p.sent_at FROM parent_sms_logs p WHERE p.user_id = l.user_id"
                    + " ORDER BY p.sent_at DESC, p.id DESC LIMIT 1) s ON TRUE"
                    + " WHERE g.child_id = :childId",
            nativeQuery = true)
    List<GuardianAccountRow> findAccountRows(@Param("childId") Long childId);

    interface GuardianAccountRow {
        Long getGuardianId();

        String getAccountStatus();

        String getSmsStatus();

        Long getSmsAtMillis();
    }
}
