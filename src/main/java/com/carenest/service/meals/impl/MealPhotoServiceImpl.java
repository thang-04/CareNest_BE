package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPhotoResponse;
import com.carenest.dto.meals.MealPhotoWindowResponse;
import com.carenest.dto.meals.MealPhotoWindowsRequest;
import com.carenest.entity.meals.MealPhoto;
import com.carenest.entity.meals.MealPhotoSettings;
import com.carenest.entity.meals.MealPhotoWindow;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.StorageService;
import com.carenest.repository.meals.MealPhotoRepository;
import com.carenest.repository.meals.MealPhotoSettingsRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MealPhotoService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MealPhotoServiceImpl implements MealPhotoService {
    private static final String LOG_PREFIX = "[MealPhotoServiceImpl]";
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final int SNIFF_BYTES = 12;

    private final MealPhotoSettingsRepository settingsRepository;
    private final MealPhotoRepository photoRepository;
    private final StorageService storageService;
    private final StaffAssignmentService staffAssignmentService;
    private final KitchenAccess kitchenAccess;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<MealPhotoWindowResponse> getWindows(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getWindows|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            kitchenAccess.checkCanRead(currentUser);
            List<MealPhotoWindowResponse> response = toResponses(settings());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<MealPhotoWindowResponse> updateWindows(CurrentUser currentUser, MealPhotoWindowsRequest request) {
        String logPrefix = LOG_PREFIX + "|updateWindows|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            // NUT-16: khung giờ là phần cố định của Hiệu trưởng, không giao được
            if (!currentUser.hasRole(CareNestRole.PRINCIPAL)
                    || !staffAssignmentService.hasActivePrincipalAssignment(
                            currentUser.userId(), LocalDate.now(clock))) {
                throw GlobalException.of(ApiCode.MEAL_PHOTO_WINDOW_FORBIDDEN);
            }
            Set<MealSession> seen = EnumSet.noneOf(MealSession.class);
            for (MealPhotoWindowsRequest.WindowInput input : request.windows()) {
                if (!input.startTime().isBefore(input.endTime()) || !seen.add(input.session())) {
                    throw GlobalException.of(ApiCode.MEAL_PHOTO_WINDOW_INVALID);
                }
            }
            var now = clock.instant();
            MealPhotoSettings settings = settings();
            settings.replaceWindows(request.windows().stream()
                    .map(input -> new MealPhotoWindow(
                            input.session(), input.startTime(), input.endTime(), currentUser.userId(), now))
                    .toList());
            settingsRepository.flush();
            List<MealPhotoWindowResponse> response = toResponses(settings);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MealPhotoResponse uploadPhoto(CurrentUser currentUser, MealSession session, MultipartFile file) {
        String logPrefix = LOG_PREFIX + "|uploadPhoto|userId=" + currentUser.userId() + "|session=" + session;
        log.info("{}|START", logPrefix);
        try {
            Long campusId = kitchenAccess.resolveWriteCampus(currentUser);
            String contentType = inspect(file);
            // NUT-16: chỉ đăng ảnh của hôm nay trong khung giờ HT cấu hình; chưa cấu hình thì chưa đăng được
            MealPhotoWindow window = settings()
                    .window(session)
                    .orElseThrow(() -> GlobalException.of(ApiCode.MEAL_PHOTO_WINDOW_NOT_CONFIGURED, label(session)));
            if (!window.contains(LocalTime.now(clock))) {
                throw GlobalException.of(ApiCode.MEAL_PHOTO_OUT_OF_WINDOW, label(session));
            }
            LocalDate today = LocalDate.now(clock);
            // Đăng cùng (điểm trường, ngày, bữa) chạy tuần tự: đăng hai lần không đụng khóa duy nhất
            photoRepository.lockCampus(campusId);
            String newKey = store(file);
            MealPhoto photo = photoRepository
                    .findByCampusIdAndPhotoDateAndSession(campusId, today, session)
                    .orElseGet(() -> MealPhoto.of(campusId, today, session));
            String oldKey = photo.getId() == null ? null : photo.getStorageKey();
            photo.replaceFile(newKey, contentType, file.getSize(), currentUser.userId(), clock.instant());
            MealPhoto saved = photoRepository.saveAndFlush(photo);
            cleanupAfterCompletion(newKey, oldKey);
            MealPhotoResponse response = toResponse(saved);
            log.info("{}|photoId={}|END", logPrefix, saved.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MealPhotoResponse> getPhotos(CurrentUser currentUser, Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getPhotos|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Long campus = kitchenAccess.resolveReadCampus(currentUser, campusId);
            LocalDate day = date == null ? LocalDate.now(clock) : date;
            List<MealPhotoResponse> response =
                    photoRepository.findByCampusIdAndPhotoDateOrderBySessionAsc(campus, day).stream()
                            .map(MealPhotoServiceImpl::toResponse)
                            .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Content getPhotoContent(CurrentUser currentUser, Long photoId) {
        String logPrefix = LOG_PREFIX + "|getPhotoContent|userId=" + currentUser.userId() + "|photoId=" + photoId;
        log.info("{}|START", logPrefix);
        try {
            MealPhoto photo = photoRepository
                    .findById(photoId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.MEAL_PHOTO_NOT_FOUND));
            kitchenAccess.resolveReadCampus(currentUser, photo.getCampusId());
            Content content = new Content(storageService.load(photo.getStorageKey()), photo.getContentType());
            log.info("{}|END", logPrefix);
            return content;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private MealPhotoSettings settings() {
        return settingsRepository
                .findById(MealPhotoSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("edu_settings singleton row is missing"));
    }

    private static List<MealPhotoWindowResponse> toResponses(MealPhotoSettings settings) {
        return settings.getWindows().stream()
                .map(w -> new MealPhotoWindowResponse(w.session(), w.startTime(), w.endTime()))
                .toList();
    }

    private static MealPhotoResponse toResponse(MealPhoto photo) {
        return new MealPhotoResponse(
                photo.getId(),
                photo.getCampusId(),
                photo.getPhotoDate(),
                photo.getSession(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getUploadedBy(),
                photo.getUploadedAt());
    }

    /** Loại ảnh theo byte đầu tệp (không tin Content-Type client gửi): JPEG, PNG, WebP, tối đa 10 MB. */
    private static String inspect(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) {
            throw GlobalException.of(ApiCode.MEAL_PHOTO_INVALID);
        }
        byte[] head;
        try (InputStream input = file.getInputStream()) {
            head = input.readNBytes(SNIFF_BYTES);
        } catch (IOException ex) {
            throw GlobalException.withCause(ApiCode.FILE_UNREADABLE, ex);
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (head.length >= 8
                && (head[0] & 0xFF) == 0x89
                && head[1] == 'P'
                && head[2] == 'N'
                && head[3] == 'G'
                && head[4] == 0x0D
                && head[5] == 0x0A
                && head[6] == 0x1A
                && head[7] == 0x0A) {
            return "image/png";
        }
        if (head.length >= 12
                && head[0] == 'R'
                && head[1] == 'I'
                && head[2] == 'F'
                && head[3] == 'F'
                && head[8] == 'W'
                && head[9] == 'E'
                && head[10] == 'B'
                && head[11] == 'P') {
            return "image/webp";
        }
        throw GlobalException.of(ApiCode.MEAL_PHOTO_INVALID);
    }

    private String store(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return storageService.store(input, file.getSize());
        } catch (IOException ex) {
            throw GlobalException.withCause(ApiCode.FILE_UNREADABLE, ex);
        }
    }

    /** Rollback ⇒ xóa tệp mới; commit ⇒ xóa tệp cũ bị thay. Xóa tệp lỗi chỉ ghi log, không làm hỏng thao tác. */
    private void cleanupAfterCompletion(String newKey, String oldKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String orphan = status == STATUS_COMMITTED ? oldKey : newKey;
                if (orphan == null) {
                    return;
                }
                try {
                    storageService.remove(orphan);
                } catch (RuntimeException ex) {
                    log.warn(
                            "{}|cleanupFile|ERROR|type={}",
                            LOG_PREFIX,
                            ex.getClass().getSimpleName());
                }
            }
        });
    }

    private static String label(MealSession session) {
        return switch (session) {
            case MORNING -> "sáng";
            case LUNCH -> "trưa";
            case AFTERNOON -> "chiều";
        };
    }
}
