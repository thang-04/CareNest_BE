package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduClassDoc;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduClassDocRepository extends JpaRepository<EduClassDoc, Long> {

    List<EduClassDoc> findAllByClassroomIdInOrderByIdAsc(Collection<Long> classroomIds);

    List<EduClassDoc> findAllByClassroomIdAndYearPlanId(Long classroomId, Long yearPlanId);

    /** Thành phần của các hồ sơ trong một trang danh sách (cùng lớp, cùng kế hoạch tổ). */
    List<EduClassDoc> findAllByClassroomIdInAndYearPlanIdIn(
            Collection<Long> classroomIds, Collection<Long> yearPlanIds);

    List<EduClassDoc> findAllByClassroomIdInAndYearPlanIdInAndKindIn(
            Collection<Long> classroomIds, Collection<Long> yearPlanIds, Collection<String> kinds);

    // Danh sách gọn (edu-list-paging, Tiền 25/11/2026): lọc + phân trang ngay ở truy vấn; cờ all* = không lọc trường đó
    String SUMMARY_WHERE = "d.classroomId in :classIds and (:allKinds = true or d.kind in :kinds)"
            + " and (:allStatuses = true or d.status in :statuses)"
            + " and (:semesterNo is null or d.semesterNo = :semesterNo) and (:themeId is null or d.themeId = :themeId)";

    // EDU-112 (4): "chỉ bản đang hiệu lực" loại hồ sơ thuộc kế hoạch tổ đã bị phiên bản mới thay thế
    String SUMMARY_PAGE_WHERE = SUMMARY_WHERE + " and (:allPlans = true or d.yearPlanId not in :excludedPlanIds)"
            // Người không soạn lớp chỉ thấy hồ sơ đã ban hành hoặc đã từng ban hành (không thấy khung chưa ban hành)
            + " and (:allDocs = true or d.classroomId in :authorClassIds"
            + " or d.status = com.carenest.utils.enums.EduClassDocStatus.APPROVED"
            + " or exists (select r.id from EduClassDocRelease r where r.classDocId = d.id))";

    @Query(
            value = "select d from EduClassDoc d where " + SUMMARY_PAGE_WHERE,
            countQuery = "select count(d) from EduClassDoc d where " + SUMMARY_PAGE_WHERE)
    org.springframework.data.domain.Page<EduClassDoc> findSummaryPage(
            @Param("classIds") Collection<Long> classIds,
            @Param("allKinds") boolean allKinds,
            @Param("kinds") Collection<String> kinds,
            @Param("allStatuses") boolean allStatuses,
            @Param("statuses") Collection<com.carenest.utils.enums.EduClassDocStatus> statuses,
            @Param("semesterNo") Integer semesterNo,
            @Param("themeId") Long themeId,
            @Param("allPlans") boolean allPlans,
            @Param("excludedPlanIds") Collection<Long> excludedPlanIds,
            @Param("allDocs") boolean allDocs,
            @Param("authorClassIds") Collection<Long> authorClassIds,
            org.springframework.data.domain.Pageable pageable);

    @Query("select d from EduClassDoc d where " + SUMMARY_WHERE + " order by d.id asc")
    List<EduClassDoc> findSummaryCandidates(
            @Param("classIds") Collection<Long> classIds,
            @Param("allKinds") boolean allKinds,
            @Param("kinds") Collection<String> kinds,
            @Param("allStatuses") boolean allStatuses,
            @Param("statuses") Collection<com.carenest.utils.enums.EduClassDocStatus> statuses,
            @Param("semesterNo") Integer semesterNo,
            @Param("themeId") Long themeId);

    @Query(
            "select distinct d.yearPlanId from EduClassDoc d where d.classroomId in :classIds and d.yearPlanId is not null")
    List<Long> findDistinctYearPlanIds(@Param("classIds") Collection<Long> classIds);

    Optional<EduClassDoc> findByDocKey(String docKey);

    List<EduClassDoc> findAllByKindInAndStatusIn(
            Collection<String> kinds, Collection<com.carenest.utils.enums.EduClassDocStatus> statuses);

    // Gửi / duyệt kế hoạch giáo dục ngày đổi trạng thái nhiều tài liệu một lúc: khóa dòng để hai người duyệt không
    // chồng nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.docKey = :docKey")
    Optional<EduClassDoc> findForUpdateByDocKey(@Param("docKey") String docKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.id = :id")
    Optional<EduClassDoc> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.id in :ids order by d.id asc")
    List<EduClassDoc> findAllByIdInForUpdateOrderByIdAsc(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.status in :statuses order by d.id asc")
    List<EduClassDoc> findAllByStatusInForUpdateOrderByIdAsc(
            @Param("statuses") Collection<com.carenest.utils.enums.EduClassDocStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EduClassDoc d where d.yearPlanId = :yearPlanId order by d.id asc")
    List<EduClassDoc> findAllByYearPlanIdForUpdateOrderByIdAsc(@Param("yearPlanId") Long yearPlanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select d from EduClassDoc d where d.classroomId = :classroomId and d.yearPlanId = :yearPlanId and (d.themeId = :themeId or (:themeId is null and d.themeId is null)) order by d.id asc")
    List<EduClassDoc> findAllByDossierForUpdateOrderByIdAsc(
            @Param("classroomId") Long classroomId,
            @Param("yearPlanId") Long yearPlanId,
            @Param("themeId") Long themeId);

    // EDU-100: giáo án đã ghi kết quả hoạt động (đánh giá sau dạy) thì không xóa theo nhánh
    @Query(
            value = "select exists(select 1 from daily_activity_results r where r.class_doc_id in (:ids))",
            nativeQuery = true)
    boolean hasActivityResults(@Param("ids") Collection<Long> ids);
}
