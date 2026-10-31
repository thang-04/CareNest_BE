package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.GoalFilePreviewResponse;
import com.carenest.entity.eduplan.EduDomain;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.eduplan.EduGoalFileService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class EduGoalFileServiceImpl implements EduGoalFileService {
    private static final String LOG_PREFIX = "[EduGoalFileServiceImpl]";
    // EDU-46: file bảng mục tiêu tối đa 2 MB
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private final EduDomainRepository domainRepository;
    private final EduPlanAccessPolicy accessPolicy;

    @Override
    @Transactional(readOnly = true)
    public GoalFilePreviewResponse preview(CurrentUser user, MultipartFile file) {
        String logPrefix = LOG_PREFIX + "|preview|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            // EDU-02/03: chỉ người lập/sửa được kế hoạch năm (Phó HT, Tổ trưởng) mới nhập file
            if (!accessPolicy.hasPermission(user, EduPermission.GOALS_EDIT)) {
                throw GlobalException.of(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN);
            }
            if (file == null || file.isEmpty()) {
                throw GlobalException.of(ApiCode.GOAL_FILE_UNSUPPORTED);
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException ex) {
                throw GlobalException.withCause(ApiCode.GOAL_FILE_UNSUPPORTED, ex);
            }
            List<String> domains = domainRepository.findAllByOrderBySortOrderAsc().stream()
                    .filter(EduDomain::isActive)
                    .map(EduDomain::getName)
                    .toList();
            GoalFilePreviewResponse preview =
                    GoalFileTable.interpret(GoalFileGrid.read(file.getOriginalFilename(), bytes), domains);
            log.info(
                    "{}|domains={}|warnings={}|END",
                    logPrefix,
                    preview.domains().size(),
                    preview.warnings().size());
            return preview;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
