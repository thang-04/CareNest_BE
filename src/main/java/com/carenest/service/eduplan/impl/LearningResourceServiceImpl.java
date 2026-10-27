package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.*;
import com.carenest.entity.eduplan.*;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.StorageService;
import com.carenest.repository.eduplan.*;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.*;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.LearningResourcePolicy;
import com.carenest.utils.enums.CareNestRole;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningResourceServiceImpl implements LearningResourceService {
    private static final String LOG_PREFIX = "[LearningResourceServiceImpl]";
    private final EduClassDocService classDocs;
    private final EduLearningResourceRepository resources;
    private final EduGoalRowRepository goals;
    private final StaffAssignmentService assignments;
    private final StorageService storage;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<LearningResourceResponse> list(CurrentUser user, String docKey) {
        String p = LOG_PREFIX + "|list|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireThemeAccess(user, docKey, false);
            boolean editor = isEditor(user, doc);
            List<LearningResourceResponse> result = resources.findAllByClassDocIdOrderByIdAsc(doc.getId()).stream()
                    .map(r -> response(doc, r))
                    .filter(r -> editor || r.active())
                    .toList();
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public LearningResourceResponse upload(
            CurrentUser user, String docKey, MultipartFile file, LearningResourceBinding binding, UUID requestId) {
        String p = LOG_PREFIX + "|upload|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireThemeAccess(user, docKey, true);
            if (requestId == null) throw new GlobalException(ApiCode.BAD_REQUEST, "Thiếu mã lần tải");
            var replay = resources.findByClassDocIdAndUploadRequestId(doc.getId(), requestId);
            if (replay.isPresent()) {
                log.info("{}|END", p);
                return response(doc, replay.get());
            }
            validateBinding(doc, binding);
            String name = LearningResourcePolicy.safeName(file.getOriginalFilename());
            String type;
            try (var input = file.getInputStream()) {
                type = LearningResourcePolicy.inspect(name, input.readNBytes(8192), file.getSize());
            } catch (IOException ex) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Không đọc được tệp");
            }
            if (name.matches("(?i).*\\.(pptx|docx|xlsx)$")) validateOffice(file, name);
            String key;
            try (var input = file.getInputStream()) {
                key = storage.store(input, file.getSize());
            } catch (IOException ex) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "Không đọc được tệp");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) storage.remove(key);
                }
            });
            EduLearningResource saved = resources.saveAndFlush(EduLearningResource.create(
                    doc.getId(),
                    binding.activityKey(),
                    binding.activitySignature(),
                    binding.activityLabel(),
                    name,
                    type,
                    file.getSize(),
                    key,
                    requestId,
                    user.userId(),
                    clock.instant()));
            log.info("{}|END", p);
            return response(doc, saved);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public LearningResourceResponse bind(CurrentUser user, String docKey, Long id, LearningResourceBinding binding) {
        String p = LOG_PREFIX + "|bind|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireThemeAccess(user, docKey, true);
            validateBinding(doc, binding);
            EduLearningResource r = requireResource(doc, id);
            r.bind(binding.activityKey(), binding.activitySignature(), binding.activityLabel());
            resources.saveAndFlush(r);
            log.info("{}|END", p);
            return response(doc, r);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void detach(CurrentUser user, String docKey, Long id) {
        String p = LOG_PREFIX + "|detach|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireThemeAccess(user, docKey, true);
            EduLearningResource r = requireResource(doc, id);
            r.detach();
            resources.saveAndFlush(r);
            log.info("{}|END", p);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Download content(CurrentUser user, String docKey, Long id) {
        String p = LOG_PREFIX + "|content|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduClassDoc doc = classDocs.requireThemeAccess(user, docKey, false);
            EduLearningResource r = requireResource(doc, id);
            if (!isEditor(user, doc) && !active(doc, r))
                throw new GlobalException(ApiCode.NOT_FOUND, "Học liệu chưa gắn với hoạt động hiện tại");
            Download result = new Download(storage.load(r.getStorageKey()), r.getOriginalName(), r.getContentType());
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private boolean isEditor(CurrentUser user, EduClassDoc doc) {
        return user.hasRole(CareNestRole.TEACHER)
                && doc.getStatus().editable()
                && assignments
                        .findCurrentTeacherClassroomIds(user.userId(), LocalDate.now(clock))
                        .contains(doc.getClassroomId());
    }

    private EduLearningResource requireResource(EduClassDoc doc, Long id) {
        return resources
                .findByIdAndClassDocId(id, doc.getId())
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Không tìm thấy học liệu"));
    }

    private void validateBinding(EduClassDoc doc, LearningResourceBinding b) {
        if (b == null
                || b.activityLabel() == null
                || b.activityLabel().isBlank()
                || b.activityLabel().length() > 1000
                || !matches(doc, b.activityKey(), b.activitySignature()))
            throw new GlobalException(
                    ApiCode.CONFLICT, "Hoạt động đã thay đổi. Lưu và chọn lại hoạt động trước khi gắn tệp.");
    }

    private boolean matches(EduClassDoc doc, String key, String signature) {
        if (key == null || signature == null || !key.matches("[0-9]+#[0-9]+")) return false;
        try {
            String[] parts = key.split("#");
            long goalId = Long.parseLong(parts[0]);
            int index = Integer.parseInt(parts[1]);
            boolean own = goals.belongsToTheme(goalId, doc.getYearPlanId(), doc.getThemeId());
            if (!own) return false;
            String text = mapper.readTree(doc.getContent())
                    .path("rows")
                    .path(parts[0])
                    .path("activities")
                    .asString("");
            return LearningResourcePolicy.signature(text).equals(signature)
                    && LearningResourcePolicy.hasActivity(text, index);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean active(EduClassDoc doc, EduLearningResource r) {
        return matches(doc, r.getActivityKey(), r.getActivitySignature());
    }

    private LearningResourceResponse response(EduClassDoc doc, EduLearningResource r) {
        return new LearningResourceResponse(
                r.getId(),
                doc.getDocKey(),
                r.getActivityKey(),
                r.getActivitySignature(),
                r.getActivityLabel(),
                r.getOriginalName(),
                r.getContentType(),
                r.getByteSize(),
                r.getUploadedBy(),
                r.getCreatedAt(),
                active(doc, r));
    }

    private void validateOffice(MultipartFile file, String name) {
        String folder = name.toLowerCase(java.util.Locale.ROOT).endsWith("pptx")
                ? "ppt/"
                : name.toLowerCase(java.util.Locale.ROOT).endsWith("docx") ? "word/" : "xl/";
        boolean types = false, body = false;
        int entries = 0;
        long expanded = 0;
        try (var zip = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10000) throw new IOException("Too many entries");
                types |= entry.getName().equals("[Content_Types].xml");
                body |= entry.getName().startsWith(folder);
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    expanded += n;
                    if (expanded > 100L * 1024 * 1024) throw new IOException("Archive too large");
                }
            }
        } catch (IOException ex) {
            throw new GlobalException(ApiCode.UNSUPPORTED_MEDIA_TYPE, "Tệp Office không hợp lệ");
        }
        if (!types || !body)
            throw new GlobalException(ApiCode.UNSUPPORTED_MEDIA_TYPE, "Nội dung Office không khớp phần mở rộng");
    }
}
