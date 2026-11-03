package com.carenest.entity.child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Số thứ tự mã trẻ theo tiền tố HS + 2 số năm; chỉ tăng bằng upsert ở ChildCodeSequenceRepository. */
@Getter
@Entity
@Table(name = "child_code_sequences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildCodeSequence {

    @Id
    @Column(name = "code_prefix", length = 10)
    private String codePrefix;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;
}
