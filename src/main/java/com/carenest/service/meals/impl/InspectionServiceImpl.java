package com.carenest.service.meals.impl;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.dto.meals.InspectionReopenRequest;
import com.carenest.dto.meals.InspectionSaveRequest;
import com.carenest.dto.meals.InspectionSheetResponse;
import com.carenest.dto.meals.InspectionSignRequest;
import com.carenest.entity.meals.InspectionEvent;
import com.carenest.entity.meals.InspectionSheet;
import com.carenest.entity.meals.InspectionSignature;
import com.carenest.exception.GlobalException;
import com.carenest.utils.enums.FileCategory;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.account.UserSignatureService;
import com.carenest.service.meals.InspectionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ImageFiles;
import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InspectionServiceImpl implements InspectionService {
    private static final String LOG_PREFIX = "[InspectionServiceImpl]";
    private static final int MIN_REASON = 3;
    private static final Set<String> HEADER_KEYS = Set.of("place", "checkedAt");
    // Mã ảnh: UUID (tệp cũ) hoặc tên tệp theo quy tắc kho (không thư mục), chỉ chữ/số/_/-/. nên an toàn khi nhúng vào JSON
    private static final Pattern IMAGE_KEY =
            Pattern.compile("[a-f0-9-]{36}|[a-z]+_[0-9A-Za-z-]{1,40}_\\d{8}-\\d{6}_[a-z0-9]{4}\\.[a-z]+");

    private final InspectionSheetRepository sheetRepository;
    private final FoodSampleRepository campusLookup;
    private final InspectionSkeleton skeleton;
    private final CampusAccessPolicy campusAccessPolicy;
    private final UserSignatureService userSignatureService;
    private final FileStorage imageStorage;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public InspectionDayResponse getDay(CurrentUser currentUser, Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getDay|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            requireCampus(campusId);
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            LocalDate day = date == null ? LocalDate.now(clock) : date;
            Map<InspectionTab, InspectionSheet> saved = new EnumMap<>(InspectionTab.class);
            sheetRepository.findByCampusIdAndSheetDate(campusId, day).forEach(s -> saved.put(s.getTab(), s));
            List<InspectionSheetResponse> sheets = new ArrayList<>();
            for (InspectionTab tab : InspectionTab.values()) {
                sheets.add(view(tab, saved.get(tab), campusId, day));
            }
            InspectionDayResponse response =
                    new InspectionDayResponse(campusId, day, canEdit(currentUser, campusId), sheets);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public InspectionSheetResponse save(
            CurrentUser currentUser, Long campusId, LocalDate date, InspectionTab tab, InspectionSaveRequest request) {
        String logPrefix =
                LOG_PREFIX + "|save|userId=" + currentUser.userId() + "|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            requireWritable(currentUser, campusId, date);
            InspectionSheet sheet = lockedSheet(campusId, date, tab);
            if (sheet != null && sheet.isSigned()) {
                throw GlobalException.of(ApiCode.INSPECTION_SIGNED);
            }
            int current = sheet == null ? 0 : sheet.getDocVersion();
            if (request.expectedVersion() != current) {
                throw GlobalException.of(ApiCode.INSPECTION_STALE);
            }
            List<Map<String, Object>> rows = InspectionColumns.sanitize(tab, request.rows());
            if (sheet == null) {
                sheet = InspectionSheet.draft(campusId, date, tab, now);
            }
            sheet.saveDraft(cleanHeader(request.header()), rows, currentUser.userId(), now);
            InspectionSheet saved = sheetRepository.saveAndFlush(sheet);
            log.info("{}|version={}|END", logPrefix, saved.getDocVersion());
            return view(tab, saved, campusId, date);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public InspectionSheetResponse sign(
            CurrentUser currentUser, Long campusId, LocalDate date, InspectionTab tab, InspectionSignRequest request) {
        String logPrefix =
                LOG_PREFIX + "|sign|userId=" + currentUser.userId() + "|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            requireWritable(currentUser, campusId, date);
            InspectionSheet sheet = lockedSheet(campusId, date, tab);
            if (sheet == null || sheet.getSheetRows().isEmpty()) {
                throw GlobalException.of(ApiCode.INSPECTION_NOT_SAVED);
            }
            if (sheet.isSigned()) {
                throw GlobalException.of(ApiCode.INSPECTION_SIGNED);
            }
            // Client chỉ lưu ô PHT nhập: bản chụp phải gộp ô hệ thống (tên món, suất...) từ nguồn lúc ký (INS-03)
            List<Map<String, Object>> rows = merge(tab, skeleton.rows(tab, campusId, date), sheet.getSheetRows());
            if (tab.requiresSensory()) {
                int missing = InspectionColumns.sensoryMissing(rows);
                if (missing > 0) {
                    throw GlobalException.of(ApiCode.INSPECTION_SENSORY_MISSING, missing);
                }
                int withoutAction = InspectionColumns.actionMissing(rows);
                if (withoutAction > 0) {
                    throw GlobalException.of(ApiCode.INSPECTION_ACTION_MISSING, withoutAction);
                }
            }
            ImageContent image = userSignatureService
                    .findForSigning(currentUser, request == null ? null : request.signatureId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.INSPECTION_SIGNATURE_REQUIRED));
            // INS-03: chép ảnh sang kho riêng của biên bản để đổi/xóa chữ ký tài khoản không làm mất ảnh trên chứng từ
            // Biên bản lưu tên tệp (thư mục cố định theo loại) để mã ảnh dùng thẳng được trên URL
            String imageKey = FileStorage.fileName(imageStorage
                    .store(FileCategory.INSPECTION_SIGNATURE, campusId, image.content())
                    .key());
            try {
                InspectionSignature signature = new InspectionSignature(
                        currentUser.userId(),
                        currentUser.fullName(),
                        now,
                        sheet.getDocVersion(),
                        imageKey,
                        image.contentType(),
                        image.content().length);
                InspectionEvent event = new InspectionEvent(
                        InspectionEvent.SIGNED, currentUser.userId(), currentUser.fullName(), now, null, imageKey);
                sheet.sign(signature, event, rows, now);
                sheetRepository.saveAndFlush(sheet);
            } catch (RuntimeException ex) {
                imageStorage.removeQuietly(FileCategory.INSPECTION_SIGNATURE, imageKey);
                throw ex;
            }
            log.info("{}|version={}|END", logPrefix, sheet.getDocVersion());
            return view(tab, sheet, campusId, date);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public InspectionSheetResponse reopen(
            CurrentUser currentUser,
            Long campusId,
            LocalDate date,
            InspectionTab tab,
            InspectionReopenRequest request) {
        String logPrefix =
                LOG_PREFIX + "|reopen|userId=" + currentUser.userId() + "|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            // Sửa lại mọi ngày được (INS-04), kể cả ngày đã qua; chỉ ngày chưa tới mới chưa có biên bản để sửa
            requireEdit(currentUser, campusId);
            requireCampus(campusId);
            String reason = request == null || request.reason() == null
                    ? ""
                    : request.reason().strip();
            if (reason.length() < MIN_REASON) {
                throw GlobalException.of(ApiCode.INSPECTION_REASON_REQUIRED);
            }
            InspectionSheet sheet = lockedSheet(campusId, date, tab);
            if (sheet == null || !sheet.isSigned()) {
                throw GlobalException.of(ApiCode.INSPECTION_NOT_SIGNED);
            }
            InspectionEvent event = new InspectionEvent(
                    InspectionEvent.REOPENED,
                    currentUser.userId(),
                    currentUser.fullName(),
                    now,
                    reason,
                    sheet.getSignature().imageKey());
            sheet.reopen(event, currentUser.userId(), now);
            sheetRepository.saveAndFlush(sheet);
            log.info("{}|version={}|END", logPrefix, sheet.getDocVersion());
            return view(tab, sheet, campusId, date);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getSignatureImage(CurrentUser currentUser, Long campusId, String imageKey) {
        String logPrefix = LOG_PREFIX + "|getSignatureImage|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            checkSignatureImage(currentUser, campusId, imageKey);
            byte[] content = imageStorage.loadBytes(FileCategory.INSPECTION_SIGNATURE, imageKey);
            String contentType = ImageFiles.sniff(content);
            if (contentType == null) {
                throw GlobalException.of(ApiCode.SIGNATURE_NOT_FOUND);
            }
            log.info("{}|END", logPrefix);
            return new ImageContent(content, contentType);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse getSignatureImageUrl(CurrentUser currentUser, Long campusId, String imageKey) {
        String logPrefix =
                LOG_PREFIX + "|getSignatureImageUrl|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            checkSignatureImage(currentUser, campusId, imageKey);
            var url = imageStorage.presignGet(FileCategory.INSPECTION_SIGNATURE, imageKey, null, null);
            FileUrlResponse result = new FileUrlResponse(url.url(), url.expiresAt());
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Ảnh chữ ký chỉ xem được khi nằm trên biên bản của điểm trường người xem được đọc. */
    private void checkSignatureImage(CurrentUser currentUser, Long campusId, String imageKey) {
        requireCampus(campusId);
        campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
        if (imageKey == null
                || !IMAGE_KEY.matcher(imageKey).matches()
                || !sheetRepository.existsWithImage(campusId, imageKey, "[{\"imageKey\":\"" + imageKey + "\"}]")) {
            throw GlobalException.of(ApiCode.SIGNATURE_NOT_FOUND);
        }
    }

    /** Bản trả về: đã ký ⇒ bản chụp lúc ký; nháp ⇒ nguồn mới nhất gộp với ô PHT đã nhập; chưa lưu ⇒ khung nguồn. */
    private InspectionSheetResponse view(InspectionTab tab, InspectionSheet sheet, Long campusId, LocalDate date) {
        if (sheet == null) {
            return new InspectionSheetResponse(
                    tab,
                    InspectionStatus.DRAFT,
                    0,
                    false,
                    Map.of(),
                    skeleton.rows(tab, campusId, date),
                    null,
                    List.of(),
                    null);
        }
        List<Map<String, Object>> rows = sheet.isSigned()
                ? sheet.getSnapshot()
                : merge(tab, skeleton.rows(tab, campusId, date), sheet.getSheetRows());
        InspectionSignature signature = sheet.getSignature();
        return new InspectionSheetResponse(
                tab,
                sheet.getStatus(),
                sheet.getDocVersion(),
                true,
                sheet.getHeader(),
                rows,
                signature == null
                        ? null
                        : new InspectionSheetResponse.Signature(
                                signature.signedBy(),
                                signature.signedByName(),
                                signature.signedAt(),
                                signature.documentVersion(),
                                signature.imageKey()),
                sheet.getHistory().stream()
                        .map(e -> new InspectionSheetResponse.Event(
                                e.action(), e.actorName(), e.at(), e.note(), e.imageKey()))
                        .toList(),
                sheet.getUpdatedAt());
    }

    /**
     * Ô hệ thống lấy lại từ nguồn mới nhất, ô PHT đã nhập giữ nguyên, dòng nguồn mới thêm vào cuối khung. Dòng PHT đã
     * lưu mà nguồn không còn (thực đơn đổi) vẫn giữ để không mất dữ liệu đã nhập (INS-04).
     */
    private static List<Map<String, Object>> merge(
            InspectionTab tab, List<Map<String, Object>> source, List<Map<String, Object>> saved) {
        Set<String> systemKeys = InspectionColumns.systemKeys(tab);
        Map<String, Map<String, Object>> savedByKey = new LinkedHashMap<>();
        saved.forEach(row -> savedByKey.put((String) row.get(InspectionColumns.KEY), row));
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> fresh : source) {
            Map<String, Object> previous = savedByKey.remove((String) fresh.get(InspectionColumns.KEY));
            Map<String, Object> row = new LinkedHashMap<>(fresh);
            if (previous != null) {
                previous.forEach((key, value) -> {
                    if (!systemKeys.contains(key)) {
                        row.put(key, value);
                    }
                });
            }
            merged.add(row);
        }
        merged.addAll(savedByKey.values());
        return merged;
    }

    private Map<String, Object> cleanHeader(Map<String, Object> header) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (header != null) {
            header.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                if (!HEADER_KEYS.contains(key) || !(value instanceof String text) || text.length() > 200) {
                    throw GlobalException.of(ApiCode.INSPECTION_ROW_INVALID, "header." + key);
                }
                clean.put(key, text);
            });
        }
        return clean;
    }

    /** Khóa điểm trường để hai người cùng ghi một mẫu chạy tuần tự, rồi đọc biên bản (null nếu chưa có). */
    private InspectionSheet lockedSheet(Long campusId, LocalDate date, InspectionTab tab) {
        sheetRepository.lockCampus(campusId);
        return sheetRepository
                .findByCampusIdAndSheetDateAndTab(campusId, date, tab)
                .orElse(null);
    }

    private void requireWritable(CurrentUser currentUser, Long campusId, LocalDate date) {
        requireEdit(currentUser, campusId);
        requireCampus(campusId);
        if (date == null || date.isAfter(LocalDate.now(clock))) {
            throw GlobalException.of(ApiCode.INSPECTION_DATE_FUTURE);
        }
    }

    private void requireEdit(CurrentUser currentUser, Long campusId) {
        if (!canEdit(currentUser, campusId)) {
            throw GlobalException.of(ApiCode.INSPECTION_ACCESS_DENIED);
        }
    }

    // INS-01, AUTH-20: FOOD_SAFETY_APPROVE theo điểm trường (HT mọi điểm trường; PHT điểm trường được giao hoặc cả
    // trường)
    private boolean canEdit(CurrentUser currentUser, Long campusId) {
        return campusAccessPolicy.canEditCampus(currentUser, Permission.FOOD_SAFETY_APPROVE, campusId);
    }

    private void requireCampus(Long campusId) {
        if (!campusLookup.campusExists(campusId)) {
            throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
        }
    }
}
