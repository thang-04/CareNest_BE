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
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.organization.RoomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
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
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms(CurrentUser currentUser, Long campusId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getRooms|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkCanViewSchool(currentUser);
            Collection<Long> campusFilter = campusId != null ? Set.of(campusId) : null;
            Long yearId = resolveSchoolYearId(schoolYearId);
            boolean closed = yearId != null
                    && schoolYearRepository
                            .findById(yearId)
                            .map(y -> y.getStatus() == SchoolYearStatus.CLOSED)
                            .orElse(false);
            if (closed) {
                // CFG-YR-02: năm đã kết thúc ⇒ phòng đúng như bản chụp lúc kết thúc năm
                List<RoomResponse> snapshot = visibleTo(currentUser, snapshotRooms(yearId, campusFilter));
                log.info("{}|snapshot|END", logPrefix);
                return snapshot;
            }
            List<Room> rooms = campusFilter == null
                    ? roomRepository.findAllByStatusNotOrderByCampusIdAscCodeAsc(RoomStatus.DELETED)
                    : roomRepository.findAllByCampusIdInAndStatusNotOrderByCampusIdAscCodeAsc(
                            campusFilter, RoomStatus.DELETED);
            List<RoomResponse> response = visibleTo(currentUser, withYearInfo(toResponses(rooms, yearId), yearId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RoomResponse createRoom(CurrentUser currentUser, RoomRequest request, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|createRoom|userId=" + currentUser.userId() + "|campusId=" + request.campusId()
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser, request.campusId());
            SchoolYear year = openYear(schoolYearId);
            // Cần chốt 19/11/2026: phòng thêm ở năm nào chỉ có ở năm đó; mã, tên không trùng trong năm
            RoomRequest data = validate(request, null, 0, year.getId());
            Room room = Room.create(
                    data.campusId(),
                    data.room(),
                    data.name(),
                    data.type(),
                    toShort(data.capacity()),
                    data.managerUserId(),
                    data.note());
            boolean active = year.getStatus() == SchoolYearStatus.ACTIVE;
            // Năm chưa hoạt động: bảng chung chưa dùng phòng này (kích hoạt năm sẽ bật)
            if (!active) {
                room.changeStatus(RoomStatus.INACTIVE);
            }
            room = roomRepository.save(room);
            roomRepository.flush();
            roomRepository.addToYear(year.getId(), room.getId());
            roomRepository.updateYearStatus(year.getId(), room.getId(), RoomStatus.ACTIVE.name());
            RoomResponse response = roomInYear(room, year.getId());
            log.info("{}|roomId={}|END", logPrefix, room.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RoomResponse updateRoom(CurrentUser currentUser, Long roomId, RoomRequest request, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|updateRoom|userId=" + currentUser.userId() + "|roomId=" + roomId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            if (schoolYearId == null) {
                RoomResponse response = updateRoom(currentUser, roomId, request);
                log.info("{}|END", logPrefix);
                return response;
            }
            Room room = findRoom(roomId);
            SchoolYear year = openYear(schoolYearId);
            RoomRepository.YearRoomInfo info = yearRoom(year.getId(), roomId);
            if (info == null) {
                throw GlobalException.of(ApiCode.ROOM_NOT_FOUND);
            }
            checkCanManage(currentUser, info.getCampusId());
            checkCanManage(currentUser, request.campusId());
            List<Classroom> yearClasses = yearClasses(roomId, year.getId());
            if (!yearClasses.isEmpty() && !info.getCampusId().equals(request.campusId())) {
                throw GlobalException.of(
                        ApiCode.ROOM_IN_USE_BY_CLASS, names(yearClasses), "chuyển sang điểm trường khác");
            }
            if (!yearClasses.isEmpty()
                    && !info.getRoomType().equals(request.type().name())) {
                throw GlobalException.of(ApiCode.ROOM_IN_USE_BY_CLASS, names(yearClasses), "đổi loại phòng");
            }
            RoomRequest data = validate(request, roomId, largestCapacity(yearClasses), year.getId());
            roomRepository.updateYearInfo(
                    year.getId(),
                    roomId,
                    data.campusId(),
                    data.room(),
                    data.name(),
                    data.type().name(),
                    toShort(data.capacity()),
                    data.managerUserId(),
                    data.note());
            // Năm đang hoạt động: bảng chung theo cùng (module khác đọc bảng chung)
            if (year.getStatus() == SchoolYearStatus.ACTIVE) {
                room.changeInfo(
                        data.campusId(),
                        data.room(),
                        data.name(),
                        data.type(),
                        toShort(data.capacity()),
                        data.managerUserId(),
                        data.note());
            }
            roomRepository.flush();
            RoomResponse response = roomInYear(room, year.getId());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RoomResponse changeRoomStatus(CurrentUser currentUser, Long roomId, RoomStatus status, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|changeRoomStatus|userId=" + currentUser.userId() + "|roomId=" + roomId
                + "|status=" + status + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            if (status == RoomStatus.DELETED) {
                throw GlobalException.of(ApiCode.METHOD_NOT_ALLOWED);
            }
            if (schoolYearId == null) {
                RoomResponse response = changeRoomStatus(currentUser, roomId, status);
                log.info("{}|END", logPrefix);
                return response;
            }
            Room room = findRoom(roomId);
            SchoolYear year = openYear(schoolYearId);
            RoomRepository.YearRoomInfo info = yearRoom(year.getId(), roomId);
            if (info == null) {
                throw GlobalException.of(ApiCode.ROOM_NOT_FOUND);
            }
            checkCanManage(currentUser, info.getCampusId());
            List<Classroom> yearClasses = yearClasses(roomId, year.getId());
            if (status == RoomStatus.INACTIVE && !yearClasses.isEmpty()) {
                throw GlobalException.of(ApiCode.ROOM_IN_USE_BY_CLASS, names(yearClasses), "ngừng sử dụng");
            }
            roomRepository.updateYearStatus(year.getId(), roomId, status.name());
            if (year.getStatus() == SchoolYearStatus.ACTIVE) {
                room.changeStatus(status);
            }
            roomRepository.flush();
            RoomResponse response = roomInYear(room, year.getId());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteRoom(CurrentUser currentUser, Long roomId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|deleteRoom|userId=" + currentUser.userId() + "|roomId=" + roomId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            if (schoolYearId == null) {
                deleteRoom(currentUser, roomId);
                log.info("{}|END", logPrefix);
                return;
            }
            Room room = findRoom(roomId);
            SchoolYear year = openYear(schoolYearId);
            RoomRepository.YearRoomInfo info = yearRoom(year.getId(), roomId);
            if (info == null) {
                throw GlobalException.of(ApiCode.ROOM_NOT_FOUND);
            }
            checkCanManage(currentUser, info.getCampusId());
            List<Classroom> yearClasses = yearClasses(roomId, year.getId());
            if (!yearClasses.isEmpty()) {
                throw GlobalException.of(ApiCode.ROOM_IN_USE_BY_CLASS, names(yearClasses), "xóa");
            }
            // Xóa ở một năm: chỉ bỏ khỏi năm đó; không còn ở năm nào thì xóa như trước
            roomRepository.removeFromYear(year.getId(), roomId);
            roomRepository.flush();
            if (year.getStatus() == SchoolYearStatus.ACTIVE) {
                room.changeStatus(RoomStatus.INACTIVE);
            }
            if (roomRepository.countYears(roomId) == 0) {
                checkNoOpenYearClass(roomId, "xóa");
                if (classroomRepository.existsByRoomId(roomId)) {
                    room.markDeleted(clock.instant());
                } else {
                    roomRepository.delete(room);
                }
            }
            roomRepository.flush();
            log.info("{}|END", logPrefix);
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
            checkCanManage(currentUser, request.campusId());
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
            Room room = findRoom(roomId);
            checkCanManage(currentUser, room.getCampusId());
            checkCanManage(currentUser, request.campusId());
            List<Classroom> openClasses = classroomRepository.findOpenYearClassesByRoomIds(List.of(roomId));
            boolean everUsed = classroomRepository.existsByRoomId(roomId);
            if (everUsed && !room.getCampusId().equals(request.campusId())) {
                throw GlobalException.of(ApiCode.ROOM_IN_USE_BY_CLASS, "một lớp", "chuyển sang điểm trường khác");
            }
            if (everUsed && room.getRoomType() != request.type()) {
                throw GlobalException.of(ApiCode.ROOM_IN_USE_BY_CLASS, "một lớp", "đổi loại phòng");
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
            if (status == RoomStatus.DELETED) {
                throw GlobalException.of(ApiCode.METHOD_NOT_ALLOWED);
            }
            Room room = findRoom(roomId);
            checkCanManage(currentUser, room.getCampusId());
            if (status == RoomStatus.INACTIVE) {
                checkNoOpenYearClass(roomId, "ngừng sử dụng");
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
            Room room = findRoom(roomId);
            checkCanManage(currentUser, room.getCampusId());
            checkNoOpenYearClass(roomId, "xóa");
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
        return validate(request, excludeId, minCapacity, null);
    }

    // Có năm học (V154): điểm trường hoạt động trong năm đó; mã, tên không trùng trong năm đó
    private RoomRequest validate(RoomRequest request, Long excludeId, int minCapacity, Long yearId) {
        if (!campusRepository.existsById(request.campusId())) {
            throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
        }
        boolean campusActive = yearId == null
                ? campusRepository.existsByIdAndStatus(request.campusId(), CampusStatus.ACTIVE)
                : Boolean.TRUE.equals(campusRepository.isActiveInYear(yearId, request.campusId()));
        if (!campusActive) {
            throw GlobalException.of(ApiCode.CAMPUS_INACTIVE);
        }
        String code = request.room().trim();
        String name = request.name().trim();
        boolean codeTaken = yearId == null
                ? roomRepository.existsCode(request.campusId(), code, excludeId)
                : roomRepository.existsCodeInYear(yearId, request.campusId(), code, excludeId);
        if (codeTaken) {
            throw GlobalException.of(ApiCode.ROOM_CODE_EXISTS);
        }
        boolean nameTaken = yearId == null
                ? roomRepository.existsName(request.campusId(), name, excludeId)
                : roomRepository.existsNameInYear(yearId, request.campusId(), name, excludeId);
        if (nameTaken) {
            throw GlobalException.of(ApiCode.ROOM_NAME_EXISTS);
        }
        if (request.type() == RoomType.CLASS && request.capacity() == null) {
            throw GlobalException.of(ApiCode.ROOM_CAPACITY_REQUIRED);
        }
        if (request.capacity() != null && request.capacity() < minCapacity) {
            throw GlobalException.of(ApiCode.ROOM_CAPACITY_BELOW_CLASS_SIZE, minCapacity);
        }
        // Phòng học: người phụ trách = giáo viên chủ nhiệm của lớp đang dùng phòng, không lưu riêng
        Long managerUserId = request.type() == RoomType.CLASS ? null : request.managerUserId();
        LocalDate referenceDate = yearReferenceDate(yearId);
        if (managerUserId != null
                && !roomRepository.isStaffOfCampus(managerUserId, request.campusId(), referenceDate)) {
            throw GlobalException.of(ApiCode.ROOM_MANAGER_INVALID);
        }
        if (managerUserId != null
                && request.type() == RoomType.KITCHEN
                && !roomRepository.isKitchenManagerOfCampus(managerUserId, request.campusId(), referenceDate)) {
            throw GlobalException.of(ApiCode.ROOM_KITCHEN_MANAGER_INVALID);
        }
        String note = request.note() == null ? "" : request.note().trim();
        return new RoomRequest(request.campusId(), code, name, request.type(), request.capacity(), managerUserId, note);
    }

    private LocalDate yearReferenceDate(Long yearId) {
        LocalDate today = schoolConfigAccess.today();
        if (yearId == null) {
            return today;
        }
        return schoolYearRepository
                .findById(yearId)
                .map(year -> today.isBefore(year.getStartDate()) ? year.getStartDate() : today)
                .orElse(today);
    }

    private void checkNoOpenYearClass(Long roomId, String action) {
        List<Classroom> openClasses = classroomRepository.findOpenYearClassesByRoomIds(List.of(roomId));
        if (!openClasses.isEmpty()) {
            String names =
                    openClasses.stream().map(Classroom::getName).distinct().collect(Collectors.joining(", "));
            throw GlobalException.of(ApiCode.ROOM_IN_USE_BY_CLASS, names, action);
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
                    // Lý do chưa ngừng / xóa được: tên lớp (năm chưa kết thúc) đang dùng phòng, không lặp
                    List<String> blockers =
                            classes.stream().map(Classroom::getName).distinct().toList();
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

    private SchoolYear openYear(Long schoolYearId) {
        Long yearId = resolveSchoolYearId(schoolYearId);
        SchoolYear year =
                yearId == null ? null : schoolYearRepository.findById(yearId).orElse(null);
        if (year == null) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND);
        }
        if (year.getStatus() == SchoolYearStatus.CLOSED) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_CLOSED, year.getName());
        }
        return year;
    }

    private RoomRepository.YearRoomInfo yearRoom(Long yearId, Long roomId) {
        return roomRepository.findYearRoomInfoById(yearId, roomId).orElse(null);
    }

    private List<Classroom> yearClasses(Long roomId, Long yearId) {
        return classroomRepository.findOpenYearClassesByRoomIds(List.of(roomId)).stream()
                .filter(c -> yearId.equals(c.getSchoolYearId()))
                .toList();
    }

    private static String names(List<Classroom> classes) {
        return classes.stream().map(Classroom::getName).distinct().collect(Collectors.joining(", "));
    }

    private RoomResponse roomInYear(Room room, Long yearId) {
        return withYearInfo(toResponses(List.of(room), yearId), yearId).stream()
                .findFirst()
                .orElseThrow(() -> GlobalException.of(ApiCode.ROOM_NOT_FOUND));
    }

    // Năm còn mở có danh sách phòng riêng (V154): chỉ phòng của năm, thông tin của năm, lý do chặn theo lớp năm đó
    private List<RoomResponse> withYearInfo(List<RoomResponse> rooms, Long yearId) {
        if (yearId == null) {
            return rooms;
        }
        Map<Long, RoomRepository.YearRoomInfo> info = roomRepository.findYearRoomInfo(yearId).stream()
                .collect(Collectors.toMap(RoomRepository.YearRoomInfo::getRoomId, i -> i));
        Set<Long> managerIds = info.values().stream()
                .map(RoomRepository.YearRoomInfo::getManagerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> managerNames = managerIds.isEmpty()
                ? Map.of()
                : roomRepository.findUserNames(managerIds).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.UserName::getUserId, RoomRepository.UserName::getFullName));
        return rooms.stream()
                .filter(r -> info.containsKey(r.id()))
                .map(r -> {
                    RoomRepository.YearRoomInfo i = info.get(r.id());
                    RoomType type = RoomType.valueOf(i.getRoomType());
                    // Phòng học: người phụ trách = GV chủ nhiệm lớp dùng phòng (đã tính trong r)
                    boolean homeroom = type == RoomType.CLASS;
                    return new RoomResponse(
                            r.id(),
                            i.getCampusId(),
                            i.getCode(),
                            i.getName(),
                            type,
                            i.getCapacity() == null ? null : i.getCapacity().intValue(),
                            homeroom ? r.managerUserId() : i.getManagerUserId(),
                            homeroom
                                    ? r.managerName()
                                    : i.getManagerUserId() == null ? null : managerNames.get(i.getManagerUserId()),
                            RoomStatus.valueOf(i.getStatus()),
                            i.getNote(),
                            r.yearClasses(),
                            r.usage(),
                            r.yearClasses(),
                            r.minCapacity());
                })
                .toList();
    }

    // Phân quyền động (Cần chốt 10/11/2026): PHT xem phòng cơ sở mình; HT cấp ROOM_MANAGE toàn trường thì xem mọi cơ sở
    private List<RoomResponse> visibleTo(CurrentUser user, List<RoomResponse> rooms) {
        Map<Long, Boolean> allowed = new HashMap<>();
        return rooms.stream()
                .filter(r -> allowed.computeIfAbsent(
                        r.campusId(),
                        id -> campusAccessPolicy.canViewCampus(user, id)
                                || campusAccessPolicy.canEditCampus(user, Permission.ROOM_MANAGE, id)))
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
        Room room = roomRepository.findById(roomId).orElseThrow(() -> GlobalException.of(ApiCode.ROOM_NOT_FOUND));
        if (room.getStatus() == RoomStatus.DELETED) {
            throw GlobalException.of(ApiCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    private void checkCanManage(CurrentUser user, Long campusId) {
        if (!campusAccessPolicy.canEditCampus(user, Permission.ROOM_MANAGE, campusId)) {
            throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
        }
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }
}
