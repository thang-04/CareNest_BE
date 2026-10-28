package com.carenest.controller.organization;

import com.carenest.dto.organization.RoomRequest;
import com.carenest.dto.organization.RoomResponse;
import com.carenest.dto.organization.RoomStatusRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.RoomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cấu hình trường › Phòng ban (FE gọi là locations). Số phòng của một trường nhỏ nên không phân trang. */
@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
@Slf4j
public class RoomController {
    private static final String LOG_PREFIX = "[RoomController]";

    private final RoomService roomService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<RoomResponse>>> getRooms(
            @RequestParam(required = false) Long campusId,
            @RequestParam(name = "schoolYearId", required = false) Long schoolYearIdParam,
            @RequestParam(required = false) Long schoolYear,
            CurrentUser currentUser) {
        // FE cũ gửi tham số schoolYear; nhận cả hai tên
        Long schoolYearId = schoolYearIdParam != null ? schoolYearIdParam : schoolYear;
        String logPrefix = LOG_PREFIX + "|getRooms|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<RoomResponse> rooms = roomService.getRooms(currentUser, campusId, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get rooms success", rooms);
    }

    @PostMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    // Để OpenAPI ghi 201 khớp ApiCode.CREATED
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<RoomResponse>> createRoom(
            @Valid @RequestBody RoomRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createRoom|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        RoomResponse room = roomService.createRoom(currentUser, request);
        log.info("{}|roomId={}|END", logPrefix, room.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create room success", room);
    }

    @PutMapping("/{roomId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<RoomResponse>> updateRoom(
            @PathVariable Long roomId, @Valid @RequestBody RoomRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateRoom|userId=" + currentUser.userId() + "|roomId=" + roomId;
        log.info("{}|START", logPrefix);
        RoomResponse room = roomService.updateRoom(currentUser, roomId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update room success", room);
    }

    @PatchMapping("/{roomId}/status")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<RoomResponse>> changeRoomStatus(
            @PathVariable Long roomId, @Valid @RequestBody RoomStatusRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|changeRoomStatus|userId=" + currentUser.userId() + "|roomId=" + roomId;
        log.info("{}|START", logPrefix);
        RoomResponse room = roomService.changeRoomStatus(currentUser, roomId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Change room status success", room);
    }

    @DeleteMapping("/{roomId}")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<Void>> deleteRoom(@PathVariable Long roomId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteRoom|userId=" + currentUser.userId() + "|roomId=" + roomId;
        log.info("{}|START", logPrefix);
        roomService.deleteRoom(currentUser, roomId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete room success");
    }
}
