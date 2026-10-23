package com.carenest.service.organization.impl;

import com.carenest.dto.organization.RoomRequest;
import com.carenest.dto.organization.RoomResponse;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.Room;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.RoomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RoomServiceImpl implements RoomService {
    private static final String LOG_PREFIX = "[RoomServiceImpl]";

    private final RoomRepository roomRepository;
    private final ClassroomRepository classroomRepository;
    private final CampusRepository campusRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final SchoolConfigAccess schoolConfigAccess;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms(CurrentUser currentUser, Long campusId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getRooms|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> viewable = schoolConfigAccess.viewableCampusIds(currentUser);
            if (campusId != null && viewable != null && !viewable.contains(campusId)) {
                throw new GlobalException(ApiCode.FORBIDDEN, "You do not have access to this campus");
            }
            Collection<Long> campusFilter = campusId != null ? Set.of(campusId) : viewable;
            Long yearId = resolveSchoolYearId(schoolYearId);
            boolean closed = yearId != null
                    && schoolYearRepository
                            .findById(yearId)
                            .map(y -> y.getStatus() == SchoolYearStatus.CLOSED)
                            .orElse(false);
            if (closed) {
                // CFG-YR-02: năm đã kết thúc ⇒ phòng đúng như bản chụp lúc kết thúc năm
                List<RoomResponse> snapshot = snapshotRooms(yearId, campusFilter);
                log.info("{}|snapshot|END", logPrefix);
                return snapshot;
            }
            List<Room> rooms = campusFilter == null
                    ? roomRepository.findAllByStatusNotOrderByCampusIdAscCodeAsc(RoomStatus.DELETED)
                    : roomRepository.findAllByCampusIdInAndStatusNotOrderByCampusIdAscCodeAsc(
                            campusFilter, RoomStatus.DELETED);
            List<RoomResponse> response = toResponses(rooms, yearId);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RoomResponse createRoom(CurrentUser currentUser, RoomRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createRoom|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            RoomRequest data = validate(request, null, 0);
            Room room = roomRepository.save(Room.create(
                    data.campusId(),
                    data.room(),
                    data.name(),
                    data.type(),
                    toShort(data.capacity()),
                    data.managerUserId(),
                    data.note()));
            RoomResponse response =
                    toResponses(List.of(room), resolveSchoolYearId(null)).getFirst();
            log.info("{}|roomId={}|END", logPrefix, room.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RoomResponse updateRoom(CurrentUser currentUser, Long roomId, RoomRequest request) {
        String logPrefix = LOG_PREFIX + "|updateRoom|userId=" + currentUser.userId() + "|roomId=" + roomId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            Room room = findRoom(roomId);
            List<Classroom> openClasses = classroomRepository.findOpenYearClassesByRoomIds(List.of(roomId));
            boolean everUsed = classroomRepository.existsByRoomId(roomId);
            if (everUsed && !room.getCampusId().equals(request.campusId())) {
                throw new GlobalException(
                        ApiCode.CONFLICT, "Room is used by a class and cannot move to another campus");
            }
            if (everUsed && room.getRoomType() != request.type()) {
                throw new GlobalException(ApiCode.CONFLICT, "Room is used by a class and cannot change type");
            }
            RoomRequest data = validate(request, roomId, largestCapacity(openClasses));
            room.changeInfo(
                    data.campusId(),
                    data.room(),
                    data.name(),
                    data.type(),
                    toShort(data.capacity()),
                    data.managerUserId(),
                    data.note());
            roomRepository.flush();
            RoomResponse response =
                    toResponses(List.of(room), resolveSchoolYearId(null)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RoomResponse changeRoomStatus(CurrentUser currentUser, Long roomId, RoomStatus status) {
        String logPrefix = LOG_PREFIX + "|changeRoomStatus|userId=" + currentUser.userId() + "|roomId=" + roomId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            if (status == RoomStatus.DELETED) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Use DELETE to remove a room");
            }
            Room room = findRoom(roomId);
            if (status == RoomStatus.INACTIVE) {
                checkNoOpenYearClass(roomId, "deactivated");
            }
            room.changeStatus(status);
            roomRepository.flush();
            RoomResponse response =
                    toResponses(List.of(room), resolveSchoolYearId(null)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteRoom(CurrentUser currentUser, Long roomId) {
        String logPrefix = LOG_PREFIX + "|deleteRoom|userId=" + currentUser.userId() + "|roomId=" + roomId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            Room room = findRoom(roomId);
            checkNoOpenYearClass(roomId, "deleted");
            // Chưa lớp nào từng dùng ⇒ xóa hẳn; lớp năm cũ còn trỏ tới ⇒ xóa mềm để lịch sử còn tên phòng
            if (classroomRepository.existsByRoomId(roomId)) {
                room.markDeleted(clock.instant());
                roomRepository.flush();
            } else {
                roomRepository.delete(room);
                roomRepository.flush();
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private RoomRequest validate(RoomRequest request, Long excludeId, int minCapacity) {
        if (!campusRepository.existsById(request.campusId())) {
            throw new GlobalException(ApiCode.NOT_FOUND, "Campus not found");
        }
        if (!campusRepository.existsByIdAndStatus(request.campusId(), CampusStatus.ACTIVE)) {
            throw new GlobalException(ApiCode.CONFLICT, "Campus is inactive");
        }
        String code = request.room().trim();
        String name = request.name().trim();
        if (roomRepository.existsCode(request.campusId(), code, excludeId)) {
            throw new GlobalException(ApiCode.CONFLICT, "Campus already has a room with this code");
        }
        if (roomRepository.existsName(request.campusId(), name, excludeId)) {
            throw new GlobalException(ApiCode.CONFLICT, "Campus already has a room with this name");
        }
        if (request.type() == RoomType.CLASS && request.capacity() == null) {
            throw new GlobalException(ApiCode.BAD_REQUEST, "Classroom type room needs a capacity");
        }
        if (request.capacity() != null && request.capacity() < minCapacity) {
            throw new GlobalException(
                    ApiCode.CONFLICT,
                    "A class using this room has max size " + minCapacity + "; capacity cannot be lower");
        }
        // Phòng học: người phụ trách = giáo viên chủ nhiệm của lớp đang dùng phòng, không lưu riêng
        Long managerUserId = request.type() == RoomType.CLASS ? null : request.managerUserId();
        if (managerUserId != null
                && !roomRepository.isStaffOfCampus(managerUserId, request.campusId(), schoolConfigAccess.today())) {
            throw new GlobalException(ApiCode.BAD_REQUEST, "Person in charge must be staff working at this campus");
        }
        String note = request.note() == null ? "" : request.note().trim();
        return new RoomRequest(request.campusId(), code, name, request.type(), request.capacity(), managerUserId, note);
    }

    private void checkNoOpenYearClass(Long roomId, String action) {
        List<Classroom> openClasses = classroomRepository.findOpenYearClassesByRoomIds(List.of(roomId));
        if (!openClasses.isEmpty()) {
            String names = openClasses.stream().map(Classroom::getName).collect(Collectors.joining(", "));
            throw new GlobalException(ApiCode.CONFLICT, "Room cannot be " + action + ": it is the room of " + names);
        }
    }

    private List<RoomResponse> snapshotRooms(Long yearId, Collection<Long> campusFilter) {
        List<SchoolYearRepository.RoomSnapshot> rows = schoolYearRepository.findRoomSnapshot(yearId).stream()
                .filter(r -> campusFilter == null || campusFilter.contains(r.getCampusId()))
                .toList();
        Set<Long> managerIds = rows.stream()
                .map(SchoolYearRepository.RoomSnapshot::getManagerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> managerNames = managerIds.isEmpty()
                ? Map.of()
                : roomRepository.findUserNames(managerIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        Map<Long, List<String>> yearClasses =
                classroomRepository.findAllBySchoolYearIdOrderByCampusIdAscNameAsc(yearId).stream()
                        .filter(c -> c.getRoomId() != null)
                        .collect(Collectors.groupingBy(
                                Classroom::getRoomId, Collectors.mapping(Classroom::getName, Collectors.toList())));
        return rows.stream()
                .map(r -> new RoomResponse(
                        r.getRoomId(),
                        r.getCampusId(),
                        r.getCode(),
                        r.getName(),
                        RoomType.valueOf(r.getRoomType()),
                        r.getCapacity() == null ? null : r.getCapacity().intValue(),
                        r.getManagerUserId(),
                        r.getManagerUserId() == null ? null : managerNames.get(r.getManagerUserId()),
                        RoomStatus.valueOf(r.getStatus()),
                        r.getNote(),
                        yearClasses.getOrDefault(r.getRoomId(), List.of()),
                        Map.of(),
                        List.of(),
                        0))
                .toList();
    }

    private List<RoomResponse> toResponses(List<Room> rooms, Long schoolYearId) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(Room::getId).toList();
        // usage theo dạng FE: số bản ghi đang trỏ tới phòng (hiện chỉ có lớp, mọi năm học)
        Map<Long, Long> classUsage = classroomRepository.countByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(
                        ClassroomRepository.RoomUsage::getRoomId, ClassroomRepository.RoomUsage::getTotal));
        Map<Long, List<Classroom>> openClasses = classroomRepository.findOpenYearClassesByRoomIds(roomIds).stream()
                .collect(Collectors.groupingBy(Classroom::getRoomId));
        Set<Long> managerIds = rooms.stream()
                .map(Room::getManagerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> managerNames = managerIds.isEmpty()
                ? Map.of()
                : roomRepository.findUserNames(managerIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        Map<Long, RoomRepository.RoomHomeroom> homerooms = schoolYearId == null
                ? Map.of()
                : roomRepository.findHomeroomTeachers(roomIds, schoolYearId, schoolConfigAccess.today()).stream()
                        .collect(Collectors.toMap(RoomRepository.RoomHomeroom::getRoomId, h -> h, (a, b) -> a));
        return rooms.stream()
                .map(room -> {
                    RoomRepository.RoomHomeroom homeroom =
                            room.getRoomType() == RoomType.CLASS ? homerooms.get(room.getId()) : null;
                    List<Classroom> classes = openClasses.getOrDefault(room.getId(), List.of());
                    List<String> yearClasses = classes.stream()
                            .filter(c -> c.getSchoolYearId().equals(schoolYearId))
                            .map(Classroom::getName)
                            .toList();
                    List<String> blockers = classes.isEmpty()
                            ? List.of()
                            : List.of("Room of "
                                    + classes.stream().map(Classroom::getName).collect(Collectors.joining(", ")));
                    // Phòng học: người phụ trách = GV chủ nhiệm của lớp dùng phòng trong năm học
                    return new RoomResponse(
                            room.getId(),
                            room.getCampusId(),
                            room.getCode(),
                            room.getName(),
                            room.getRoomType(),
                            room.getCapacity() == null
                                    ? null
                                    : room.getCapacity().intValue(),
                            homeroom != null ? homeroom.getUserId() : room.getManagerUserId(),
                            homeroom != null
                                    ? homeroom.getFullName()
                                    : room.getManagerUserId() == null
                                            ? null
                                            : managerNames.get(room.getManagerUserId()),
                            room.getStatus(),
                            room.getNote(),
                            yearClasses,
                            classUsage.containsKey(room.getId())
                                    ? Map.of("classes", classUsage.get(room.getId()))
                                    : Map.of(),
                            blockers,
                            largestCapacity(classes));
                })
                .toList();
    }

    private Long resolveSchoolYearId(Long schoolYearId) {
        if (schoolYearId != null) {
            return schoolYearId;
        }
        return schoolYearRepository
                .findFirstByStatus(SchoolYearStatus.ACTIVE)
                .map(SchoolYear::getId)
                .orElse(null);
    }

    private static int largestCapacity(List<Classroom> classes) {
        return classes.stream()
                .map(Classroom::getCapacity)
                .filter(Objects::nonNull)
                .mapToInt(Short::intValue)
                .max()
                .orElse(0);
    }

    private Room findRoom(Long roomId) {
        Room room = roomRepository
                .findById(roomId)
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Room not found"));
        if (room.getStatus() == RoomStatus.DELETED) {
            throw new GlobalException(ApiCode.NOT_FOUND, "Room not found");
        }
        return room;
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }
}
