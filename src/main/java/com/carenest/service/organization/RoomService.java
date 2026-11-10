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
}
