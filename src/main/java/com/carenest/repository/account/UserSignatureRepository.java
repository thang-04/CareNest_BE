package com.carenest.repository.account;

import com.carenest.entity.account.UserSignature;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSignatureRepository extends JpaRepository<UserSignature, Long> {

    /** Mặc định trước, rồi mới nhất trước. */
    @Query("select s from UserSignature s where s.userId = :userId order by s.defaultSignature desc, s.id desc")
    List<UserSignature> findMine(@Param("userId") Long userId);

    Optional<UserSignature> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Khóa dòng tài khoản tới hết transaction: thêm, xóa, đổi mặc định của cùng một người chạy tuần tự. */
    @Query(value = "select id from user_accounts where id = :userId for update", nativeQuery = true)
    Long lockUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update UserSignature s set s.defaultSignature = false where s.userId = :userId and s.defaultSignature = true")
    int clearDefault(@Param("userId") Long userId);
}
