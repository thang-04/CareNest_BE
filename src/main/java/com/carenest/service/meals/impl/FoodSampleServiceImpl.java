package com.carenest.service.meals.impl;

import com.carenest.dto.meals.FoodSampleDisposeRequest;
import com.carenest.dto.meals.FoodSampleRequest;
import com.carenest.dto.meals.FoodSampleResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.FoodSample;
import com.carenest.entity.meals.FoodSampleDishDetail;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.FoodSampleService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FoodSampleDisposalStatus;
import com.carenest.utils.enums.FoodSampleStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FoodSampleServiceImpl implements FoodSampleService {
    private static final String LOG_PREFIX = "[FoodSampleServiceImpl]";
    private static final String NONE = "NONE";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String UP_TO_DATE = "UP_TO_DATE";
    private static final int MAX_PERIOD_DAYS = 93;

    private final FoodSampleRepository sampleRepository;
    private final DishRepository dishRepository;
    private final KitchenAccess kitchenAccess;
    private final BoardingWriteGuard writeGuard;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<FoodSampleResponse> getSamples(CurrentUser currentUser, Long campusId, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getSamples|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Long campus = kitchenAccess.resolveReadCampus(currentUser, campusId);
            if (from == null || to == null || to.isBefore(from)) {
                throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
            }
            if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
                throw GlobalException.of(ApiCode.DATE_RANGE_TOO_LONG, MAX_PERIOD_DAYS);
            }
            List<FoodSample> samples =
                    sampleRepository.findByCampusIdAndSampleDateBetweenOrderBySampleDateDescSampledAtDescIdDesc(
                            campus, from, to);
            List<FoodSampleResponse> response = toResponses(currentUser, samples);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodSampleResponse createSample(CurrentUser currentUser, FoodSampleRequest request) {
        String logPrefix = LOG_PREFIX + "|createSample|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            Long campusId = kitchenAccess.resolveWriteCampus(currentUser);
            Instant now = clock.instant();
            Instant sampledAt = validateRequest(request, now, now);
            requireToday(request.date(), sampledAt);
            requireSampleUnsigned(campusId, request.date());
            FoodSample saved = sampleRepository.save(FoodSample.record(
                    campusId,
                    request.date(),
                    request.session(),
                    sampledAt,
                    currentUser.userId(),
                    noteOf(request),
                    request.dishIds(),
                    detailsOf(request)));
            FoodSampleResponse response =
                    toResponses(currentUser, List.of(saved)).getFirst();
            log.info("{}|sampleId={}|END", logPrefix, saved.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodSampleResponse updateSample(CurrentUser currentUser, Long sampleId, FoodSampleRequest request) {
        String logPrefix = LOG_PREFIX + "|updateSample|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        try {
            FoodSample sample = requireKitchenSample(currentUser, sampleId);
            // NUT-21: bản đã duyệt hoặc đã ghi hủy là chứng từ, bếp không sửa
            if (!sample.isEditable()) {
                throw GlobalException.of(ApiCode.FOOD_SAMPLE_LOCKED);
            }
            // NUT-21: không dời mẫu sang ngày khác; mẫu của ngày đã qua không sửa lùi; thiếu giờ lấy mẫu thì giữ giờ cũ
            if (!request.date().equals(sample.getSampleDate())) {
                throw GlobalException.of(ApiCode.FOOD_SAMPLE_DATE_CHANGED);
            }
            Instant sampledAt = validateRequest(request, clock.instant(), sample.getSampledAt());
            requireToday(sample.getSampleDate(), sampledAt);
            requireSampleUnsigned(sample.getCampusId(), sample.getSampleDate());
            sample.change(
                    request.date(),
                    request.session(),
                    sampledAt,
                    noteOf(request),
                    request.dishIds(),
                    detailsOf(request));
            sampleRepository.saveAndFlush(sample);
            FoodSampleResponse response =
                    toResponses(currentUser, List.of(sample)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodSampleResponse disposeSample(CurrentUser currentUser, Long sampleId, FoodSampleDisposeRequest request) {
        String logPrefix = LOG_PREFIX + "|disposeSample|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        try {
            FoodSample sample = requireKitchenSample(currentUser, sampleId);
            if (sample.getDisposalStatus() != FoodSampleDisposalStatus.NONE) {
                throw GlobalException.of(ApiCode.FOOD_SAMPLE_STATE_INVALID);
            }
            requireSampleUnsigned(sample.getCampusId(), sample.getSampleDate());
            Instant now = clock.instant();
            Instant disposedAt = request == null || request.disposedAt() == null ? now : request.disposedAt();
            if (disposedAt.isAfter(now)) {
                throw GlobalException.of(ApiCode.INVALID_VALUE, "disposedAt");
            }
            // NUT-21: chỉ hủy mẫu sau ít nhất 24 giờ kể từ lúc lấy mẫu
            if (disposedAt.isBefore(sample.disposableFrom())) {
                throw GlobalException.of(ApiCode.FOOD_SAMPLE_DISPOSAL_TOO_EARLY);
            }
            sample.recordDisposal(currentUser.userId(), disposedAt);
            sampleRepository.saveAndFlush(sample);
            FoodSampleResponse response =
                    toResponses(currentUser, List.of(sample)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodSampleResponse approveSample(CurrentUser currentUser, Long sampleId) {
        String logPrefix = LOG_PREFIX + "|approveSample|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        try {
            FoodSample sample = requireApprovableSample(currentUser, sampleId);
            if (sample.getStatus() != FoodSampleStatus.RECORDED) {
                throw GlobalException.of(ApiCode.FOOD_SAMPLE_STATE_INVALID);
            }
            sample.approve(currentUser.userId(), clock.instant());
            sampleRepository.saveAndFlush(sample);
            FoodSampleResponse response =
                    toResponses(currentUser, List.of(sample)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FoodSampleResponse approveDisposal(CurrentUser currentUser, Long sampleId) {
        String logPrefix = LOG_PREFIX + "|approveDisposal|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        try {
            FoodSample sample = requireApprovableSample(currentUser, sampleId);
            if (sample.getDisposalStatus() != FoodSampleDisposalStatus.RECORDED) {
                throw GlobalException.of(ApiCode.FOOD_SAMPLE_STATE_INVALID);
            }
            sample.approveDisposal(currentUser.userId(), clock.instant());
            sampleRepository.saveAndFlush(sample);
            FoodSampleResponse response =
                    toResponses(currentUser, List.of(sample)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalStatus getApprovalStatus(Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getApprovalStatus|campusId=" + campusId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            int pending = (int) (sampleRepository.countByCampusIdAndStatus(campusId, FoodSampleStatus.RECORDED)
                    + sampleRepository.countByCampusIdAndDisposalStatus(campusId, FoodSampleDisposalStatus.RECORDED));
            String status;
            if (pending > 0) {
                status = PENDING_APPROVAL;
            } else {
                status = sampleRepository.existsByCampusIdAndSampleDate(campusId, date) ? UP_TO_DATE : NONE;
            }
            log.info("{}|END", logPrefix);
            return new ApprovalStatus(status, pending);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Bếp chỉ thao tác bản của điểm trường mình; bản không tồn tại hoặc của điểm trường khác đều báo như nhau. */
    private FoodSample requireKitchenSample(CurrentUser currentUser, Long sampleId) {
        Long campusId = kitchenAccess.resolveWriteCampus(currentUser);
        return sampleRepository
                .findById(sampleId)
                .filter(s -> s.getCampusId().equals(campusId))
                .orElseThrow(() -> GlobalException.of(ApiCode.FOOD_SAMPLE_NOT_FOUND));
    }

    // AUTH-20: FOOD_SAFETY_APPROVE theo điểm trường (HT mọi điểm trường; PHT điểm trường được giao hoặc cả trường)
    private FoodSample requireApprovableSample(CurrentUser currentUser, Long sampleId) {
        FoodSample sample = sampleRepository
                .findById(sampleId)
                .orElseThrow(() -> GlobalException.of(ApiCode.FOOD_SAMPLE_NOT_FOUND));
        if (!campusAccessPolicy.canEditCampus(currentUser, Permission.FOOD_SAFETY_APPROVE, sample.getCampusId())) {
            throw GlobalException.of(ApiCode.FOOD_SAFETY_ACCESS_DENIED);
        }
        return sample;
    }

    /** Kiểm đầu vào ghi/sửa; trả giờ lấy mẫu (bỏ trống ⇒ {@code defaultSampledAt}). */
    private Instant validateRequest(FoodSampleRequest request, Instant now, Instant defaultSampledAt) {
        Instant sampledAt = request.sampledAt() == null ? defaultSampledAt : request.sampledAt();
        if (sampledAt.isAfter(now)) {
            throw GlobalException.of(ApiCode.INVALID_VALUE, "sampledAt");
        }
        if (request.date().isAfter(LocalDate.now(clock))) {
            throw GlobalException.of(ApiCode.INVALID_DATE_RANGE);
        }
        Set<Long> dishIds = Set.copyOf(request.dishIds());
        if (dishRepository.findAllById(dishIds).size() != dishIds.size()) {
            throw GlobalException.of(ApiCode.FOOD_SAMPLE_DISH_INVALID);
        }
        // Ghi chú Mẫu 5: mỗi món một dòng và chỉ cho món đã chọn lưu mẫu
        if (request.dishDetails() != null) {
            Set<Long> described = new HashSet<>();
            for (FoodSampleRequest.DishDetailInput detail : request.dishDetails()) {
                if (!dishIds.contains(detail.dishId()) || !described.add(detail.dishId())) {
                    throw GlobalException.of(ApiCode.INVALID_VALUE, "dishDetails");
                }
            }
        }
        return sampledAt;
    }

    // NUT-21: chỉ ghi lưu mẫu cho hôm nay, giờ lấy mẫu cùng ngày với ngày ghi
    private void requireToday(LocalDate date, Instant sampledAt) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(date)
                || !sampledAt.atZone(clock.getZone()).toLocalDate().equals(date)) {
            throw GlobalException.of(ApiCode.FOOD_SAMPLE_DATE_NOT_TODAY, today);
        }
    }

    // INS-03: khóa điểm trường rồi mới xem M5 đã ký chưa, để không ghi lọt giữa lúc PHT đang ký
    private void requireSampleUnsigned(Long campusId, LocalDate date) {
        writeGuard.lock(campusId);
        writeGuard.requireUnsigned(campusId, date, ApiCode.FOOD_SAMPLE_INSPECTION_SIGNED, InspectionTab.SAMPLE);
    }

    private static String noteOf(FoodSampleRequest request) {
        return request.note() == null ? "" : request.note().trim();
    }

    /** null ⇒ client không gửi: giữ ghi chú từng món đã có. */
    private static List<FoodSampleDishDetail> detailsOf(FoodSampleRequest request) {
        if (request.dishDetails() == null) {
            return null;
        }
        return request.dishDetails().stream()
                .map(detail -> new FoodSampleDishDetail(
                        detail.dishId(),
                        trimmed(detail.weight()),
                        trimmed(detail.container()),
                        trimmed(detail.temperature())))
                .toList();
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private List<FoodSampleResponse> toResponses(CurrentUser currentUser, Collection<FoodSample> samples) {
        Set<Long> dishIds =
                samples.stream().flatMap(sample -> sample.getDishIds().stream()).collect(Collectors.toSet());
        Map<Long, Dish> dishes = dishIds.isEmpty()
                ? Map.of()
                : dishRepository.findAllById(dishIds).stream()
                        .collect(Collectors.toMap(Dish::getId, Function.identity()));
        LocalDate today = LocalDate.now(clock);
        Map<Long, Boolean> signedByCampus = new HashMap<>();
        return samples.stream()
                .map(sample -> toResponse(
                        currentUser,
                        sample,
                        dishes,
                        // Chỉ mẫu sửa được của hôm nay mới cần hỏi biên bản M5 (một lần mỗi điểm trường)
                        sample.isEditable()
                                && today.equals(sample.getSampleDate())
                                && !signedByCampus.computeIfAbsent(
                                        sample.getCampusId(),
                                        campusId -> writeGuard.signed(campusId, today, InspectionTab.SAMPLE))))
                .toList();
    }

    private FoodSampleResponse toResponse(
            CurrentUser currentUser, FoodSample sample, Map<Long, Dish> dishes, boolean editable) {
        Map<Long, FoodSampleDishDetail> details = sample.getDishDetails().stream()
                .collect(Collectors.toMap(FoodSampleDishDetail::dishId, Function.identity(), (a, b) -> a));
        List<FoodSampleResponse.SampleDish> sampleDishes = sample.getDishIds().stream()
                .map(dishes::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Dish::getName))
                .map(dish -> {
                    FoodSampleDishDetail detail = details.get(dish.getId());
                    return new FoodSampleResponse.SampleDish(
                            dish.getId(),
                            dish.getCode(),
                            dish.getName(),
                            detail == null ? "" : detail.weight(),
                            detail == null ? "" : detail.container(),
                            detail == null ? "" : detail.temperature());
                })
                .toList();
        return new FoodSampleResponse(
                sample.getId(),
                sample.getCampusId(),
                sample.getSampleDate(),
                sample.getSession(),
                sample.getSampledAt(),
                sampleDishes,
                sample.getNote(),
                sample.getRecordedBy(),
                sample.getStatus(),
                sample.getApprovedBy(),
                sample.getApprovedAt(),
                sample.disposableFrom(),
                sample.getDisposedAt(),
                sample.getDisposalRecordedBy(),
                sample.getDisposalStatus(),
                sample.getDisposalApprovedBy(),
                sample.getDisposalApprovedAt(),
                campusAccessPolicy.canEditCampus(currentUser, Permission.FOOD_SAFETY_APPROVE, sample.getCampusId()),
                editable);
    }
}
