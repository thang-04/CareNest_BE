package com.carenest.service.notification.impl;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.notification.DeviceTokenRequest;
import com.carenest.dto.notification.NotificationListResponse;
import com.carenest.dto.notification.NotificationResponse;
import com.carenest.entity.notification.DeviceToken;
import com.carenest.entity.notification.Notification;
import com.carenest.exception.GlobalException;
import com.carenest.repository.notification.DeviceTokenRepository;
import com.carenest.repository.notification.NotificationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {
    private static final String LOG_PREFIX = "[NotificationServiceImpl]";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int TOKEN_MAX = 512;

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    public void notifyUsers(Collection<Long> userIds, NotificationType type, String title, String body, Long childId) {
        String logPrefix = LOG_PREFIX + "|notifyUsers|type=" + type + "|users=" + userIds.size();
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            List<Notification> created = new ArrayList<>();
            for (Long userId : new LinkedHashSet<>(userIds)) {
                if (userId != null) {
                    created.add(notificationRepository.save(Notification.of(userId, type, title, body, childId, now)));
                }
            }
            if (!created.isEmpty()) {
                // Đẩy tới thiết bị sau khi giao dịch tạo thông báo đã lưu (PushDispatchListener)
                eventPublisher.publishEvent(new NotificationsCreatedEvent(
                        created.stream().map(Notification::getId).toList()));
            }
            log.info("{}|created={}|END", logPrefix, created.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse listMyNotifications(CurrentUser currentUser, int limit) {
        String logPrefix = LOG_PREFIX + "|listMyNotifications|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            NotificationListResponse list = listOf(currentUser.userId(), limit);
            log.info("{}|END", logPrefix);
            return list;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public NotificationResponse markRead(CurrentUser currentUser, Long notificationId) {
        String logPrefix =
                LOG_PREFIX + "|markRead|userId=" + currentUser.userId() + "|notificationId=" + notificationId;
        log.info("{}|START", logPrefix);
        try {
            // Thông báo của người khác trả 404 như không tồn tại
            Notification n = notificationRepository
                    .findByIdAndUserId(notificationId, currentUser.userId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.NOTIFICATION_NOT_FOUND));
            n.markRead(clock.instant());
            log.info("{}|END", logPrefix);
            return toView(n);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public NotificationListResponse markAllRead(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|markAllRead|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            notificationRepository
                    .findByUserIdAndReadAtIsNull(currentUser.userId())
                    .forEach(n -> n.markRead(now));
            NotificationListResponse list = listOf(currentUser.userId(), DEFAULT_LIMIT);
            log.info("{}|END", logPrefix);
            return list;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void registerDevice(CurrentUser currentUser, DeviceTokenRequest request) {
        String logPrefix = LOG_PREFIX + "|registerDevice|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            String token = checkToken(request);
            if (request.platform() == null) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA,
                        List.of(new FieldErrorResponse("platform", "Chọn nền tảng thiết bị")));
            }
            Instant now = clock.instant();
            // Máy đăng nhập người khác thì token chuyển sang người đó
            deviceTokenRepository
                    .findByToken(token)
                    .ifPresentOrElse(
                            d -> d.assignTo(currentUser.userId(), request.platform(), now),
                            () -> deviceTokenRepository.save(
                                    DeviceToken.of(currentUser.userId(), token, request.platform(), now)));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void unregisterDevice(CurrentUser currentUser, DeviceTokenRequest request) {
        String logPrefix = LOG_PREFIX + "|unregisterDevice|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            String token = checkToken(request);
            deviceTokenRepository
                    .findByToken(token)
                    .filter(d -> Objects.equals(d.getUserId(), currentUser.userId()))
                    .ifPresent(deviceTokenRepository::delete);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private NotificationListResponse listOf(Long userId, int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<NotificationResponse> items =
                notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, size)).stream()
                        .map(NotificationServiceImpl::toView)
                        .toList();
        return new NotificationListResponse(items, notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    private static String checkToken(DeviceTokenRequest request) {
        String token = request == null || request.token() == null
                ? ""
                : request.token().trim();
        if (token.isEmpty() || token.length() > TOKEN_MAX) {
            throw GlobalException.withFieldErrors(
                    ApiCode.INVALID_REQUEST_DATA,
                    List.of(new FieldErrorResponse("token", "Thiếu hoặc sai token thiết bị (tối đa 512 ký tự)")));
        }
        return token;
    }

    private static NotificationResponse toView(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getChildId(), n.getCreatedAt(), n.getReadAt());
    }
}
