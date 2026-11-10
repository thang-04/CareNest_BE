package com.carenest.repository.child;

import com.carenest.entity.child.ChildCodeSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildCodeSequenceRepository extends JpaRepository<ChildCodeSequence, String> {

    /** Tăng số thứ tự của tiền tố (tạo dòng nếu chưa có); dòng bị khóa tới hết giao dịch nên lưu đồng thời không trùng. */
    @Modifying(flushAutomatically = true)
    @Query(
            value = "INSERT INTO child_code_sequences (code_prefix, last_number) VALUES (:prefix, 1)"
                    + " ON CONFLICT (code_prefix) DO UPDATE SET last_number = child_code_sequences.last_number + 1",
            nativeQuery = true)
    void increment(@Param("prefix") String prefix);

    @Query(value = "SELECT last_number FROM child_code_sequences WHERE code_prefix = :prefix", nativeQuery = true)
    int currentNumber(@Param("prefix") String prefix);
}
