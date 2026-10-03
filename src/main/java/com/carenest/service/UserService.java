package com.carenest.service;

import com.carenest.dto.request.CreateStaffRequest;
import com.carenest.dto.response.UserResponse;
import com.carenest.entity.Role;
import com.carenest.entity.User;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.ChildRepository;
import com.carenest.repository.UserRepository;
import com.carenest.utils.IdentifierUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<Role> STAFF_ROLES = Set.of(Role.VICE_PRINCIPAL, Role.TEACHER, Role.STAFF);

    private final UserRepository userRepository;
    private final ChildRepository childRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    /**
     * Principal or vice principal creates a staff account (vice principal, teacher or staff).
     * Only the principal can create a vice principal. The user logs in by email and must change the password first.
     */
    @Transactional
    public UserResponse createStaff(Long creatorId, CreateStaffRequest request) {
        if (!STAFF_ROLES.contains(request.role())) {
            throw new AppException(ErrorCode.ROLE_NOT_ASSIGNABLE);
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (request.role() == Role.VICE_PRINCIPAL && !creator.getRoles().contains(Role.PRINCIPAL)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        String email = IdentifierUtils.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        String phoneNumber = StringUtils.hasText(request.phoneNumber())
                ? IdentifierUtils.normalizePhone(request.phoneNumber())
                : null;
        if (phoneNumber != null && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new AppException(ErrorCode.PHONE_EXISTED);
        }

        User staff = User.builder()
                .email(email)
                .phoneNumber(phoneNumber)
                .fullName(request.fullName().trim())
                .position(StringUtils.hasText(request.position()) ? request.position().trim() : null)
                .password(passwordEncoder.encode(request.password()))
                .roles(new HashSet<>(Set.of(request.role())))
                .mustChangePassword(true)
                .build();

        return UserResponse.from(userRepository.save(staff), null);
    }

    /**
     * Principal or vice principal resets any parent's password back to the default (the parent's phone number).
     */
    @Transactional
    public void resetParentPassword(Long parentId) {
        resetToPhoneNumber(findParent(parentId));
    }

    /**
     * Teacher resets a parent's password back to the default.
     * Only allowed for parents of children the teacher added.
     */
    @Transactional
    public void resetParentPasswordByTeacher(Long teacherId, Long parentId) {
        User parent = findParent(parentId);
        if (!childRepository.existsByParentIdAndCreatedById(parentId, teacherId)) {
            throw new AppException(ErrorCode.PARENT_NOT_MANAGED);
        }
        resetToPhoneNumber(parent);
    }

    private User findParent(Long parentId) {
        return userRepository.findById(parentId)
                .filter(user -> user.getRoles().contains(Role.PARENT))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void resetToPhoneNumber(User parent) {
        parent.setPassword(passwordEncoder.encode(parent.getPhoneNumber()));
        parent.setMustChangePassword(true);
    }
}
