package com.carenest.repository.announcement;

import com.carenest.entity.announcement.SchoolAnnouncement;
import com.carenest.utils.enums.AnnouncementStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface SchoolAnnouncementRepository
        extends JpaRepository<SchoolAnnouncement, Long>, JpaSpecificationExecutor<SchoolAnnouncement> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SchoolAnnouncement> findAll(Specification<SchoolAnnouncement> spec, Sort sort);

    /** Khóa để sửa/hủy: chờ nếu worker đang giữ, rồi nơi gọi kiểm lại trạng thái. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM SchoolAnnouncement a WHERE a.id = :id")
    Optional<SchoolAnnouncement> lockById(@Param("id") Long id);

    /** Khóa thông báo đến giờ gửi; máy khác đang giữ thì bỏ qua (SKIP LOCKED). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT a FROM SchoolAnnouncement a WHERE a.id = :id AND a.status IN :states AND a.sendAt <= :now")
    Optional<SchoolAnnouncement> lockDue(
            @Param("id") Long id, @Param("states") Collection<AnnouncementStatus> states, @Param("now") Instant now);

    @Query("SELECT a.id FROM SchoolAnnouncement a WHERE a.status IN :states AND a.sendAt <= :now ORDER BY a.sendAt")
    List<Long> findDueIds(
            @Param("states") Collection<AnnouncementStatus> states, @Param("now") Instant now, Pageable pageable);

    Page<SchoolAnnouncement> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** PHT: thông báo thuộc điểm trường mình hoặc do mình gửi. */
    @Query("SELECT a FROM SchoolAnnouncement a WHERE a.senderId = :userId OR a.scopeCampusId IN :campusIds"
            + " ORDER BY a.createdAt DESC, a.id DESC")
    Page<SchoolAnnouncement> findVisibleToVicePrincipal(
            @Param("userId") Long userId, @Param("campusIds") Collection<Long> campusIds, Pageable pageable);
}
