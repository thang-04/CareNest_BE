package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Danh hiệu đề xuất cho một trẻ trong danh sách khen thưởng của lớp. */
@Getter
@Entity
@Table(name = "reward_list_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardListItem {

    private static final String SEPARATOR = "|";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "list_id", nullable = false)
    private Long listId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(nullable = false, length = 500)
    private String titles;

    @Column(length = 300)
    private String note;

    public static RewardListItem of(Long listId, Long childId, List<String> titles, String note) {
        RewardListItem i = new RewardListItem();
        i.listId = listId;
        i.childId = childId;
        i.titles = String.join(SEPARATOR, titles);
        i.note = note;
        return i;
    }

    public List<String> titleList() {
        return titles == null || titles.isEmpty()
                ? List.of()
                : Arrays.asList(titles.split(java.util.regex.Pattern.quote(SEPARATOR)));
    }
}
