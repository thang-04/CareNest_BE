package com.carenest.service.meals.impl;

import com.carenest.dto.meals.KitchenDayResponse;
import com.carenest.dto.meals.MealPhotoWeekResponse;
import com.carenest.entity.meals.FoodSample;
import com.carenest.entity.meals.InspectionSheet;
import com.carenest.entity.meals.MealPhoto;
import com.carenest.entity.meals.MealPhotoSettings;
import com.carenest.entity.meals.MealPhotoWindow;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.repository.meals.MealPhotoRepository;
import com.carenest.repository.meals.MealPhotoSettingsRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountQueryService;
import com.carenest.service.meals.KitchenDayService;
import com.carenest.service.meals.MealPhotoWeekService;
import com.carenest.utils.enums.MealSession;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealPhotoWeekServiceImpl implements MealPhotoWeekService {
    private static final String LOG_PREFIX = "[MealPhotoWeekServiceImpl]";
    private static final String POSTED = "POSTED";
    private static final String MISSING = "MISSING";
    private static final String PENDING = "PENDING";
    private static final String NOT_EXPECTED = "NOT_EXPECTED";
    private static final int SCHOOL_DAYS = 5;

    private final MealPhotoRepository photoRepository;
    private final MealPhotoSettingsRepository settingsRepository;
    private final FoodSampleRepository sampleRepository;
    private final InspectionSheetRepository sheetRepository;
    private final KitchenDayService kitchenDayService;
    private final AccountQueryService accountQueryService;
    private final KitchenAccess kitchenAccess;
    private final Clock clock;

    @Override
    public MealPhotoWeekResponse getWeek(CurrentUser currentUser, Long campusId, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|getWeek|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Long campus = kitchenAccess.resolveReadCampus(currentUser, campusId);
            LocalDate today = LocalDate.now(clock);
            LocalDate monday = (weekStart == null ? today : weekStart).with(DayOfWeek.MONDAY);
            LocalDate friday = monday.plusDays(SCHOOL_DAYS - 1L);

            List<MealPhoto> photos = photoRepository.findByCampusIdAndPhotoDateBetweenOrderByPhotoDateAscSessionAsc(
                    campus, monday, friday);
            Map<String, MealPhoto> photoByKey = new HashMap<>();
            photos.forEach(p -> photoByKey.put(key(p.getPhotoDate(), p.getSession()), p));
            Map<Long, String> names = accountQueryService.getDisplayNames(photos.stream()
                    .map(MealPhoto::getUploadedBy)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet()));
            MealPhotoSettings settings =
                    settingsRepository.findById(MealPhotoSettings.SINGLETON_ID).orElse(null);

            // Sổ lưu mẫu mới nhất của mỗi (ngày, bữa): danh sách đã xếp mới nhất trước
            Map<String, FoodSample> sampleByKey = new HashMap<>();
            sampleRepository
                    .findByCampusIdAndSampleDateBetweenOrderBySampleDateDescSampledAtDescIdDesc(campus, monday, friday)
                    .forEach(s -> sampleByKey.putIfAbsent(key(s.getSampleDate(), s.getSession()), s));
            Map<LocalDate, List<InspectionSheet>> sheetsByDate = new HashMap<>();
            sheetRepository
                    .findByCampusIdAndSheetDateBetween(campus, monday, friday)
                    .forEach(s -> sheetsByDate
                            .computeIfAbsent(s.getSheetDate(), d -> new ArrayList<>())
                            .add(s));

            List<MealPhotoWeekResponse.WeekDay> days = new ArrayList<>();
            for (int i = 0; i < SCHOOL_DAYS; i++) {
                LocalDate date = monday.plusDays(i);
                KitchenDayResponse kitchenDay = kitchenDayService.getKitchenDay(currentUser, date, campus);
                days.add(day(date, today, kitchenDay, photoByKey, names, settings, sampleByKey, sheetsByDate));
            }
            MealPhotoWeekResponse response = new MealPhotoWeekResponse(campus, monday, days);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private MealPhotoWeekResponse.WeekDay day(
            LocalDate date,
            LocalDate today,
            KitchenDayResponse kitchenDay,
            Map<String, MealPhoto> photoByKey,
            Map<Long, String> names,
            MealPhotoSettings settings,
            Map<String, FoodSample> sampleByKey,
            Map<LocalDate, List<InspectionSheet>> sheetsByDate) {
        boolean upcoming = date.isAfter(today);
        List<MealPhotoWeekResponse.WeekSession> sessions = new ArrayList<>();
        for (MealSession session : MealSession.values()) {
            List<String> dishes = dishes(kitchenDay, session);
            MealPhoto photo = photoByKey.get(key(date, session));
            Optional<MealPhotoWindow> window = settings == null ? Optional.empty() : settings.window(session);
            FoodSample sample = sampleByKey.get(key(date, session));
            sessions.add(new MealPhotoWeekResponse.WeekSession(
                    session,
                    status(photo, kitchenDay.dayOff(), dishes, upcoming, date.isBefore(today), window),
                    photo == null ? null : photo(photo, names, window),
                    dishes,
                    servings(kitchenDay, session, true),
                    servings(kitchenDay, session, false),
                    sample == null
                            ? null
                            : new MealPhotoWeekResponse.WeekSample(sample.getStatus(), sample.getDisposalStatus())));
        }
        List<MealPhotoWeekResponse.WeekInspection> inspections = sheetsByDate.getOrDefault(date, List.of()).stream()
                .sorted(Comparator.comparing(InspectionSheet::getTab))
                .map(s -> new MealPhotoWeekResponse.WeekInspection(
                        s.getTab(),
                        s.getStatus(),
                        s.getSignature() == null ? null : s.getSignature().signedByName(),
                        s.getSignature() == null ? null : s.getSignature().signedAt()))
                .toList();
        return new MealPhotoWeekResponse.WeekDay(date, kitchenDay.dayOff(), upcoming, sessions, inspections);
    }

    /** NUT-16: thiếu ảnh khi bữa có trong thực đơn đã công bố và đã hết hạn đăng; hôm nay còn khung giờ thì chờ đăng. */
    private String status(
            MealPhoto photo,
            boolean dayOff,
            List<String> dishes,
            boolean upcoming,
            boolean past,
            Optional<MealPhotoWindow> window) {
        if (photo != null) {
            return POSTED;
        }
        if (dayOff || dishes.isEmpty() || upcoming) {
            return NOT_EXPECTED;
        }
        if (past) {
            return MISSING;
        }
        boolean windowEnded =
                window.isPresent() && LocalTime.now(clock).isAfter(window.get().endTime());
        return windowEnded ? MISSING : PENDING;
    }

    private MealPhotoWeekResponse.WeekPhoto photo(
            MealPhoto photo, Map<Long, String> names, Optional<MealPhotoWindow> window) {
        Boolean onTime = window.map(w ->
                        w.contains(photo.getUploadedAt().atZone(clock.getZone()).toLocalTime()))
                .orElse(null);
        return new MealPhotoWeekResponse.WeekPhoto(
                photo.getId(),
                photo.getContentType(),
                photo.getSizeBytes(),
                photo.getUploadedAt(),
                photo.getUploadedBy(),
                photo.getUploadedBy() == null ? null : names.get(photo.getUploadedBy()),
                onTime);
    }

    private static List<String> dishes(KitchenDayResponse kitchenDay, MealSession session) {
        Set<String> seen = new HashSet<>();
        List<String> dishes = new ArrayList<>();
        kitchenDay.menu().stream()
                .filter(m -> m.session() == session)
                .flatMap(m -> m.dishes().stream())
                .forEach(d -> {
                    if (seen.add(d.name())) {
                        dishes.add(d.name());
                    }
                });
        return dishes;
    }

    /** Tổng suất đã chốt của bữa theo loại (thường hoặc thay thế); null khi bữa chưa có suất đã xác nhận. */
    private static Integer servings(KitchenDayResponse kitchenDay, MealSession session, boolean normal) {
        List<KitchenDayResponse.ClassServings> rows = kitchenDay.servings().stream()
                .filter(s -> s.session() == session)
                .toList();
        if (rows.isEmpty()) {
            return null;
        }
        return rows.stream().mapToInt(s -> normal ? s.normal() : s.substitute()).sum();
    }

    private static String key(LocalDate date, MealSession session) {
        return date + ":" + session;
    }
}
