package com.carenest.controller.child;

import com.carenest.dto.child.ChildSchoolYearResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildSchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** App phụ huynh: các năm học của con cho bộ chọn năm (AUTH-04, CFG-YR-05). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ChildSchoolYearController {
    private static final String LOG_PREFIX = "[ChildSchoolYearController]";

    private final ChildSchoolYearService childSchoolYearService;

    @GetMapping("/children/{childId}/school-years")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<List<ChildSchoolYearResponse>>> getChildSchoolYears(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChildSchoolYears|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ChildSchoolYearResponse> years = childSchoolYearService.getChildSchoolYears(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get school years of child success", years);
    }
}
