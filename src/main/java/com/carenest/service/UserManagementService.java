package com.carenest.service;

import com.carenest.dto.request.ChangeRoleRequest;
import com.carenest.dto.request.ManagerResetPasswordRequest;
import com.carenest.dto.request.UpdateUserRequest;
import com.carenest.dto.response.ChildResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.response.UserDetailResponse;
import com.carenest.dto.response.UserResponse;
import com.carenest.entity.Role;
import com.carenest.entity.User;
import com.carenest.entity.UserImage;
import com.carenest.entity.UserStatus;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.ChildRepository;
import com.carenest.repository.PickupPersonRepository;
import com.carenest.repository.UserImageRepository;
import com.carenest.repository.UserRepository;
import com.carenest.utils.IdentifierUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Account administration by the principal and vice principals.
 * <ul>
 *   <li>The principal manages everyone except themselves.</li>
 *   <li>A vice principal manages teachers, staff and parents, not the principal or other vice principals.</li>
 *   <li>Accounts with data are locked, never deleted; only an account created by mistake can be deleted.</li>
 *   <li>Changing roles, login identifiers or the password signs the user out everywhere.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final Set<Role> STAFF_ROLES = Set.of(Role.VICE_PRINCIPAL, Role.TEACHER, Role.STAFF);
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final ChildRepository childRepository;
    private final PickupPersonRepository pickupPersonRepository;
    private final UserImageRepository userImageRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role role, UserStatus status, String keyword, int page, int size) {
        String pattern = StringUtils.hasText(keyword) ? "%" + keyword.trim().toLowerCase() + "%" : null;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = userRepository.search(role, status, pattern, pageable);
        return PageResponse.from(users, this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserDetailResponse detail(Long userId) {
        User user = findUser(userId);
        List<ChildResponse> children = childRepository.findByParentIdOrderByIdAsc(userId).stream()
                .map(child -> ChildResponse.from(child, storageService.url(child.getPhotoKey()), false))
                .toList();
        return new UserDetailResponse(toResponse(user), user.getCreatedAt(), user.getLastLoginAt(), children);
    }

    /**
     * Edits name, login identifiers and position. Staff keep an email (they log in with it),
     * parents keep a phone number. Anyone in management may edit their own information.
     */
    @Transactional
    public UserResponse update(Long actorId, Long userId, UpdateUserRequest request) {
        User actor = findUser(actorId);
        User user = findUser(userId);
        if (!actorId.equals(userId)) {
            assertCanManage(actor, user);
        }

        String email = StringUtils.hasText(request.email()) ? IdentifierUtils.normalizeEmail(request.email()) : null;
        String phone = StringUtils.hasText(request.phoneNumber())
                ? IdentifierUtils.normalizePhone(request.phoneNumber())
                : null;
        if (email == null && (isStaff(user) || isPrincipal(user))) {
            throw new AppException(ErrorCode.EMAIL_REQUIRED);
        }
        if (phone == null && user.getRoles().contains(Role.PARENT)) {
            throw new AppException(ErrorCode.PHONE_REQUIRED);
        }
        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }
        if (phone != null && !phone.equals(user.getPhoneNumber()) && userRepository.existsByPhoneNumber(phone)) {
            throw new AppException(ErrorCode.PHONE_EXISTED);
        }

        boolean identifiersChanged = !Objects.equals(email, user.getEmail())
                || !Objects.equals(phone, user.getPhoneNumber());
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPosition(StringUtils.hasText(request.position()) ? request.position().trim() : null);
        if (identifiersChanged) {
            user.revokeTokens();
        }
        return toResponse(user);
    }

    /**
     * Replaces the staff role (vice principal, teacher or staff); a PARENT role is kept.
     * Only the principal promotes to or demotes from vice principal. Leaving TEACHER also drops HEAD_TEACHER.
     */
    @Transactional
    public UserResponse changeRole(Long actorId, Long userId, ChangeRoleRequest request) {
        Role role = request.role();
        if (!STAFF_ROLES.contains(role)) {
            throw new AppException(ErrorCode.ROLE_NOT_ASSIGNABLE);
        }
        User actor = findUser(actorId);
        User user = findUser(userId);
        assertNotSelf(actorId, userId);
        assertCanManage(actor, user);
        if (!isStaff(user)) {
            throw new AppException(ErrorCode.NOT_STAFF_ACCOUNT);
        }
        if (role == Role.VICE_PRINCIPAL && !isPrincipal(actor)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Set<Role> roles = user.getRoles();
        boolean keepHeadTeacher = role == Role.TEACHER && roles.contains(Role.HEAD_TEACHER);
        roles.removeAll(STAFF_ROLES);
        roles.remove(Role.HEAD_TEACHER);
        roles.add(role);
        if (keepHeadTeacher) {
            roles.add(Role.HEAD_TEACHER);
        }
        user.revokeTokens();
        return toResponse(user);
    }

    /**
     * Appoints (or removes) a teacher as head teacher. A head teacher keeps the TEACHER role.
     */
    @Transactional
    public UserResponse setHeadTeacher(Long teacherId, boolean headTeacher) {
        User teacher = userRepository.findById(teacherId)
                .filter(user -> user.getRoles().contains(Role.TEACHER))
                .orElseThrow(() -> new AppException(ErrorCode.TEACHER_NOT_FOUND));

        boolean changed = headTeacher
                ? teacher.getRoles().add(Role.HEAD_TEACHER)
                : teacher.getRoles().remove(Role.HEAD_TEACHER);
        if (changed) {
            teacher.revokeTokens();
        }
        return toResponse(teacher);
    }

    /**
     * Sets a temporary password, for example when a staff member forgot theirs.
     */
    @Transactional
    public void resetPassword(Long actorId, Long userId, ManagerResetPasswordRequest request) {
        User actor = findUser(actorId);
        User user = findUser(userId);
        assertNotSelf(actorId, userId);
        assertCanManage(actor, user);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setMustChangePassword(true);
        user.revokeTokens();
    }

    /**
     * Locks or unlocks an account instead of deleting it. A locked user is rejected from the next request on.
     */
    @Transactional
    public UserResponse changeStatus(Long actorId, Long userId, UserStatus status) {
        if (actorId.equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_CHANGE_OWN_STATUS);
        }
        User actor = findUser(actorId);
        User user = findUser(userId);
        assertCanManage(actor, user);

        user.setStatus(status);
        return toResponse(user);
    }

    /**
     * Deletes an account created by mistake: never logged in, no children, no child added, no pickup reviewed.
     * Any other account must be locked instead.
     */
    @Transactional
    public void delete(Long actorId, Long userId) {
        User actor = findUser(actorId);
        User user = findUser(userId);
        assertNotSelf(actorId, userId);
        assertCanManage(actor, user);

        if (user.getLastLoginAt() != null
                || childRepository.existsByParentId(userId)
                || childRepository.existsByCreatedById(userId)
                || pickupPersonRepository.existsByReviewedById(userId)) {
            throw new AppException(ErrorCode.USER_IN_USE);
        }

        List<UserImage> images = userImageRepository.findByUserIdOrderByCreatedAtDesc(userId);
        userImageRepository.deleteAll(images);
        userRepository.delete(user);
        images.forEach(image -> storageService.delete(image.getObjectKey()));
        storageService.delete(user.getAvatar());
    }

    private void assertNotSelf(Long actorId, Long userId) {
        if (actorId.equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_MANAGE_SELF);
        }
    }

    private static void assertCanManage(User actor, User user) {
        if (isPrincipal(user)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (user.getRoles().contains(Role.VICE_PRINCIPAL) && !isPrincipal(actor)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean isPrincipal(User user) {
        return user.getRoles().contains(Role.PRINCIPAL);
    }

    private static boolean isStaff(User user) {
        return user.getRoles().stream().anyMatch(STAFF_ROLES::contains);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, storageService.url(user.getAvatar()));
    }
}
