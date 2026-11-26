package com.carenest.controller.meals;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.meals.ParentMenuResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MealPhotoService;
import com.carenest.service.meals.ParentMenuService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** App phụ huynh: thực đơn tuần và ảnh bữa của con (PAR-01, PAR-14, NUT-16, AUTH-04). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ParentMenuController {
    private static final String LOG_PREFIX = "[ParentMenuController]";

    private final ParentMenuService parentMenuService;

    @GetMapping("/children/{childId}/menu")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<ParentMenuResponse>> getWeek(
            @PathVariable Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getWeek|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ParentMenuResponse week = parentMenuService.getWeek(currentUser, childId, weekStart);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child weekly menu success", week);
    }

    @GetMapping("/children/{childId}/meal-photos/{photoId}/url")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<FileUrlResponse>> getPhotoUrl(
            @PathVariable Long childId, @PathVariable Long photoId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPhotoUrl|userId=" + currentUser.userId() + "|childId=" + childId
                + "|photoId=" + photoId;
        log.info("{}|START", logPrefix);
        FileUrlResponse url = parentMenuService.getPhotoUrl(currentUser, childId, photoId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child meal photo url success", url);
    }

    @GetMapping("/children/{childId}/meal-photos/{photoId}/content")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<Resource> getPhotoContent(
            @PathVariable Long childId, @PathVariable Long photoId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPhotoContent|userId=" + currentUser.userId() + "|childId=" + childId
                + "|photoId=" + photoId;
        log.info("{}|START", logPrefix);
        MealPhotoService.Content content = parentMenuService.getPhotoContent(currentUser, childId, photoId);
        log.info("{}|END", logPrefix);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }
}
