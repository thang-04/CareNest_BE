package com.carenest.service.announcement.impl;

import com.carenest.dto.announcement.AnnouncementTemplateRequest;
import com.carenest.dto.announcement.AnnouncementTemplateResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.announcement.AnnouncementTemplate;
import com.carenest.exception.GlobalException;
import com.carenest.repository.announcement.AnnouncementTemplateRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementTemplateService;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementTemplateServiceImpl implements AnnouncementTemplateService {
    private static final String LOG_PREFIX = "[AnnouncementTemplateServiceImpl]";

    private final AnnouncementTemplateRepository templateRepository;
    private final TemplateUsage templateUsage;
    private final RecipientResolver recipientResolver;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementTemplateResponse> listTemplates(CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|listTemplates|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            boolean wholeSchool = recipientResolver.managesWholeSchool(user);
            Set<Long> managed = wholeSchool ? Set.of() : recipientResolver.managedCampusIds(user);
            List<AnnouncementTemplate> rows = wholeSchool
                    ? templateRepository.findAllByOrderByNameAscIdAsc()
                    : templateRepository.findVisible(withDummy(managed));
            List<AnnouncementTemplateResponse> list = rows.stream()
                    .map(t -> toView(t, wholeSchool || (t.getCampusId() != null && managed.contains(t.getCampusId()))))
                    .toList();
            log.info("{}|count={}|END", logPrefix, list.size());
            return list;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementTemplateResponse createTemplate(CurrentUser user, AnnouncementTemplateRequest request) {
        String logPrefix = LOG_PREFIX + "|createTemplate|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            validate(request);
            checkCanManage(user, request.campusId());
            AnnouncementTemplate t = templateRepository.save(AnnouncementTemplate.create(
                    request.name().trim(),
                    request.title().trim(),
                    request.body().trim(),
                    request.campusId(),
                    user.userId(),
                    clock.instant()));
            log.info("{}|templateId={}|END", logPrefix, t.getId());
            return toView(t, true);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementTemplateResponse updateTemplate(
            CurrentUser user, Long templateId, AnnouncementTemplateRequest request) {
        String logPrefix = LOG_PREFIX + "|updateTemplate|userId=" + user.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        try {
            AnnouncementTemplate t = manageable(user, templateId);
            validate(request);
            checkCanManage(user, request.campusId());
            Instant now = clock.instant();
            t.edit(request.name().trim(), request.title().trim(), request.body().trim(), request.campusId(), now);
            log.info("{}|END", logPrefix);
            return toView(t, true);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteTemplate(CurrentUser user, Long templateId) {
        String logPrefix = LOG_PREFIX + "|deleteTemplate|userId=" + user.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        try {
            AnnouncementTemplate t = manageable(user, templateId);
            if (templateUsage.isUsed(t.getId())) {
                throw GlobalException.of(ApiCode.ANNOUNCEMENT_TEMPLATE_IN_USE);
            }
            templateRepository.delete(t);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Mẫu người dùng sửa/xóa được; không thấy hoặc không được sửa ⇒ 404 / 403. */
    private AnnouncementTemplate manageable(CurrentUser user, Long templateId) {
        AnnouncementTemplate t = templateRepository
                .findById(templateId)
                .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_TEMPLATE_NOT_FOUND));
        if (!recipientResolver.managesWholeSchool(user)) {
            Set<Long> managed = recipientResolver.managedCampusIds(user);
            if (t.getCampusId() == null && !managed.isEmpty()) {
                throw GlobalException.of(ApiCode.ANNOUNCEMENT_SCOPE_DENIED);
            }
            if (t.getCampusId() == null || !managed.contains(t.getCampusId())) {
                throw GlobalException.of(ApiCode.ANNOUNCEMENT_TEMPLATE_NOT_FOUND);
            }
        }
        return t;
    }

    /** Mẫu toàn trường chỉ HT; PHT chỉ mẫu của điểm trường mình (FR-017). */
    private void checkCanManage(CurrentUser user, Long campusId) {
        if (recipientResolver.managesWholeSchool(user)) {
            return;
        }
        if (campusId == null || !recipientResolver.managedCampusIds(user).contains(campusId)) {
            throw GlobalException.of(ApiCode.ANNOUNCEMENT_SCOPE_DENIED);
        }
    }

    private static void validate(AnnouncementTemplateRequest r) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (r == null || blank(r.name()) || r.name().trim().length() > 100) {
            errors.add(new FieldErrorResponse("name", "Tên mẫu bắt buộc, tối đa 100 ký tự"));
        }
        if (r == null || blank(r.title()) || r.title().trim().length() > 150) {
            errors.add(new FieldErrorResponse("title", "Tiêu đề bắt buộc, tối đa 150 ký tự"));
        }
        if (r == null || blank(r.body()) || r.body().trim().length() > 4000) {
            errors.add(new FieldErrorResponse("body", "Nội dung bắt buộc, tối đa 4000 ký tự"));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
    }

    private static AnnouncementTemplateResponse toView(AnnouncementTemplate t, boolean canEdit) {
        return new AnnouncementTemplateResponse(
                t.getId(), t.getName(), t.getTitle(), t.getBody(), t.getCampusId(), t.getUpdatedAt(), canEdit);
    }

    private static Set<Long> withDummy(Set<Long> ids) {
        Set<Long> result = new HashSet<>(ids);
        result.add(-1L);
        return result;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
