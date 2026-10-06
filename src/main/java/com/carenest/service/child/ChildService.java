package com.carenest.service.child;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.entity.child.Child;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.child.ChildMapper;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildService {

    private final ChildRepository childRepository;
    private final EnrollmentService enrollmentService;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildMapper childMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId) {
        String prefix = "[getChildProfile]|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", prefix);

        // Kiểm phạm vi TRƯỚC khi load: id ngoài phạm vi và id không tồn tại cùng trả 403 (chống dò id)
        childAccessPolicy.checkCanViewChild(currentUser, childId);
        Child child = childRepository
                .findById(childId)
                .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Child not found"));
        Long currentClassroomId = enrollmentService.findActiveClassroomIds(childId, LocalDate.now(clock)).stream()
                .findFirst()
                .orElse(null);

        ChildProfileResponse response = childMapper.toProfileResponse(child, currentClassroomId);
        log.info("{}|END", prefix);
        return response;
    }
}
