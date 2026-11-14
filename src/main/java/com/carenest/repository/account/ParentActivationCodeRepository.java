package com.carenest.repository.account;

import com.carenest.entity.account.ParentActivationCode;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParentActivationCodeRepository extends JpaRepository<ParentActivationCode, Long> {

    // Khóa dòng khi kiểm/đếm lần sai: hai lần nhập đồng thời không vượt quá giới hạn 5 lần
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ParentActivationCode c where c.userId = :userId")
    Optional<ParentActivationCode> findForUpdate(@Param("userId") Long userId);
}
