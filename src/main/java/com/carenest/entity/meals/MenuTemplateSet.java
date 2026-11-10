package com.carenest.entity.meals;

import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.MenuBlock;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Bộ thực đơn mẫu 4 tuần (thứ 2–6) của một khối, có nhãn mùa (NUT-13). */
@Getter
@Entity
@Table(name = "menu_template_sets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuTemplateSet {

    public static final int WEEKS = 4;
    public static final int WEEKDAYS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "season_label", nullable = false, length = 50)
    private String seasonLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_block", nullable = false, length = 20)
    private MenuBlock menuBlock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CatalogStatus status = CatalogStatus.ACTIVE;

    @OneToMany(mappedBy = "set", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("weekNo, weekday, session, sortOrder")
    private Set<MenuTemplateItem> items = new LinkedHashSet<>();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static MenuTemplateSet create(Long createdBy) {
        MenuTemplateSet set = new MenuTemplateSet();
        set.createdBy = createdBy;
        set.updatedBy = createdBy;
        return set;
    }

    public void changeInfo(String name, String seasonLabel, MenuBlock menuBlock, Long updatedBy) {
        this.name = name;
        this.seasonLabel = seasonLabel;
        this.menuBlock = menuBlock;
        this.updatedBy = updatedBy;
    }

    /** Thay cả lưới 4 tuần; người gọi flush giữa hai lần để không trùng khóa (bộ, tuần, ngày, bữa, món) khi chèn. */
    public void replaceItems(List<MenuTemplateItem> newItems) {
        items.clear();
        newItems.forEach(item -> {
            item.attachTo(this);
            items.add(item);
        });
    }

    public void changeStatus(CatalogStatus status, Long updatedBy) {
        this.status = status;
        this.updatedBy = updatedBy;
    }
}
