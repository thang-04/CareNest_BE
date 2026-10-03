package com.carenest.service;

import com.carenest.dto.request.ChangeChildParentRequest;
import com.carenest.dto.request.CreateChildRequest;
import com.carenest.dto.request.UpdateChildRequest;
import com.carenest.dto.response.ChildDetailResponse;
import com.carenest.dto.response.ChildResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.entity.Child;
import com.carenest.entity.Role;
import com.carenest.entity.User;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.ChildRepository;
import com.carenest.repository.UserRepository;
import com.carenest.utils.IdentifierUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChildService {

    private final ChildRepository childRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final PickupPersonService pickupPersonService;

    /**
     * Principal or vice principal adds a child with the parent's phone number.
     * - Phone already has an account: the child is linked to it (siblings share one parent account).
     * - Otherwise a PARENT account is created, default password = phone number, must change at first login.
     */
    @Transactional
    public ChildResponse createChild(Long creatorId, CreateChildRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ParentLookup parent = findOrCreateParent(request.parentPhone(), request.parentName());

        Child child = childRepository.save(Child.builder()
                .fullName(request.childName().trim())
                .dateOfBirth(request.dateOfBirth())
                .parent(parent.user())
                .createdBy(creator)
                .build());

        return ChildResponse.from(child, null, parent.created());
    }

    @Transactional(readOnly = true)
    public PageResponse<ChildResponse> list(String keyword, int page, int size) {
        String pattern = StringUtils.hasText(keyword) ? "%" + keyword.trim().toLowerCase() + "%" : null;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(childRepository.search(pattern, pageable),
                child -> ChildResponse.from(child, storageService.url(child.getPhotoKey()), false));
    }

    @Transactional(readOnly = true)
    public ChildDetailResponse detail(Long childId) {
        Child child = findChild(childId);
        return new ChildDetailResponse(ChildResponse.from(child, storageService.url(child.getPhotoKey()), false),
                child.getCreatedAt(), pickupPersonService.listAll(childId));
    }

    @Transactional
    public ChildResponse update(Long childId, UpdateChildRequest request) {
        Child child = findChild(childId);
        child.setFullName(request.childName().trim());
        child.setDateOfBirth(request.dateOfBirth());
        return ChildResponse.from(child, storageService.url(child.getPhotoKey()), false);
    }

    /**
     * Moves the child to another parent account (for example when the phone number was entered wrong).
     * The pickup persons were added by the previous parent, so each one needs a new approval.
     */
    @Transactional
    public ChildResponse changeParent(Long childId, ChangeChildParentRequest request) {
        Child child = findChild(childId);
        ParentLookup parent = findOrCreateParent(request.parentPhone(), request.parentName());
        if (!parent.user().getId().equals(child.getParent().getId())) {
            child.setParent(parent.user());
            pickupPersonService.requireReapproval(childId);
        }
        return ChildResponse.from(child, storageService.url(child.getPhotoKey()), parent.created());
    }

    @Transactional(readOnly = true)
    public List<ChildResponse> listForParent(Long parentId) {
        return childRepository.findByParentIdOrderByIdAsc(parentId).stream()
                .map(child -> ChildResponse.from(child, storageService.url(child.getPhotoKey()), false))
                .toList();
    }

    /**
     * Parent updates the photo of their own child.
     */
    @Transactional
    public ChildResponse updatePhotoByParent(Long parentId, Long childId, MultipartFile photo) {
        Child child = childRepository.findByIdAndParentId(childId, parentId)
                .orElseThrow(() -> new AppException(ErrorCode.CHILD_NOT_FOUND));
        return replacePhoto(child, photo);
    }

    /**
     * Principal or vice principal updates the photo of any child.
     */
    @Transactional
    public ChildResponse updatePhoto(Long childId, MultipartFile photo) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new AppException(ErrorCode.CHILD_NOT_FOUND));
        return replacePhoto(child, photo);
    }

    /**
     * Phone already has an account: reuse it (siblings share one parent account; a staff member may also be
     * a parent and gets the PARENT role). Otherwise create a PARENT account with password = phone number.
     */
    private ParentLookup findOrCreateParent(String parentPhone, String parentName) {
        String phone = IdentifierUtils.normalizePhone(parentPhone);
        Optional<User> existing = userRepository.findByPhoneNumber(phone);
        User parent = existing.orElseGet(() -> userRepository.save(User.builder()
                .phoneNumber(phone)
                .fullName(parentName.trim())
                .password(passwordEncoder.encode(phone))
                .roles(new HashSet<>(Set.of(Role.PARENT)))
                .mustChangePassword(true)
                .build()));
        parent.getRoles().add(Role.PARENT);
        return new ParentLookup(parent, existing.isEmpty());
    }

    private Child findChild(Long childId) {
        return childRepository.findById(childId)
                .orElseThrow(() -> new AppException(ErrorCode.CHILD_NOT_FOUND));
    }

    private record ParentLookup(User user, boolean created) {
    }

    private ChildResponse replacePhoto(Child child, MultipartFile photo) {
        String oldKey = child.getPhotoKey();
        child.setPhotoKey(storageService.uploadImage(photo, "children/" + child.getId()));
        storageService.delete(oldKey);
        return ChildResponse.from(child, storageService.url(child.getPhotoKey()), false);
    }
}
