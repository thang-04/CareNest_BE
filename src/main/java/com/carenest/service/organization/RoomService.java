package com.carenest.service.organization;

import com.carenest.dto.organization.RoomRequest;
import com.carenest.dto.organization.RoomResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.RoomStatus;
import java.util.List;

/** Cấu hình trường › Phòng ban: HT ghi, PHT xem phòng của điểm trường được phân công. */
public interface RoomService {

    List<RoomResponse> getRooms(CurrentUser currentUser, Long campusId, Long schoolYearId);

    RoomResponse createRoom(CurrentUser currentUser, RoomRequest request);

    RoomResponse updateRoom(CurrentUser currentUser, Long roomId, RoomRequest request);

    RoomResponse changeRoomStatus(CurrentUser currentUser, Long roomId, RoomStatus status);

    void deleteRoom(CurrentUser currentUser, Long roomId);

    // Theo năm học (V154, Cần chốt 19/11/2026): thêm, sửa, ngừng, xóa ở năm nào chỉ năm đó; năm đang hoạt động cập
    // nhật cả bảng chung
    RoomResponse createRoom(CurrentUser currentUser, RoomRequest request, Long schoolYearId);

    RoomResponse updateRoom(CurrentUser currentUser, Long roomId, RoomRequest request, Long schoolYearId);

    RoomResponse changeRoomStatus(CurrentUser currentUser, Long roomId, RoomStatus status, Long schoolYearId);

    void deleteRoom(CurrentUser currentUser, Long roomId, Long schoolYearId);
}
