package com.carenest.service.child;

import com.carenest.dto.child.ChildSchoolYearResponse;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.Optional;

/** Năm học của trẻ phụ huynh được thấy (AUTH-04, CFG-YR-05); module khác dùng để lọc dữ liệu phụ huynh theo năm. */
public interface ChildSchoolYearService {

    List<ChildSchoolYearResponse> getChildSchoolYears(CurrentUser currentUser, Long childId);

    /** Năm trẻ có học mà phụ huynh được thấy; không có ⇒ {@code SCHOOL_YEAR_NOT_FOUND}. */
    ChildSchoolYearResponse requireChildYear(CurrentUser currentUser, Long childId, Long schoolYearId);

    /** Như {@link #requireChildYear} không kiểm người xem (caller đã kiểm quyền trẻ); rỗng khi trẻ không học năm đó. */
    Optional<ChildSchoolYearResponse> findChildYear(Long childId, Long schoolYearId);
}
