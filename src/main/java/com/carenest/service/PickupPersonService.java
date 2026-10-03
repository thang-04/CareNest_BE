package com.carenest.service;

import com.carenest.dto.request.PickupPersonRequest;
import com.carenest.dto.response.PickupPersonResponse;
import com.carenest.entity.Child;
import com.carenest.entity.PickupPerson;
import com.carenest.entity.PickupStatus;
import com.carenest.entity.User;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.ChildRepository;
import com.carenest.repository.PickupPersonRepository;
import com.carenest.repository.UserRepository;
import com.carenest.utils.IdentifierUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

/**
 * People allowed to pick up a child. The parent adds or edits them (status goes back to PENDING);
 * the principal or a vice principal approves or rejects. Teachers see only APPROVED ones.
 */
@Service
@RequiredArgsConstructor
public class PickupPersonService {

    private final PickupPersonRepository pickupPersonRepository;
    private final ChildRepository childRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<PickupPersonResponse> listForParent(Long parentId, Long childId) {
        Child child = findOwnChild(parentId, childId);
        return pickupPersonRepository.findByChildIdOrderByCreatedAtAsc(child.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PickupPersonResponse add(Long parentId, Long childId, PickupPersonRequest request, MultipartFile photo) {
        Child child = findOwnChild(parentId, childId);
        PickupPerson person = PickupPerson.builder()
                .child(child)
                .fullName(request.fullName().trim())
                .relationship(request.relationship().trim())
                .phoneNumber(IdentifierUtils.normalizePhone(request.phoneNumber()))
                .photoKey(storageService.uploadImage(photo, "pickup-persons/" + childId))
                .build();
        return toResponse(pickupPersonRepository.save(person));
    }

    /**
     * Any change by the parent needs a new approval. The photo is optional here.
     */
    @Transactional
    public PickupPersonResponse update(Long parentId, Long pickupPersonId, PickupPersonRequest request,
                                       MultipartFile photo) {
        PickupPerson person = findOwnPickupPerson(parentId, pickupPersonId);
        person.setFullName(request.fullName().trim());
        person.setRelationship(request.relationship().trim());
        person.setPhoneNumber(IdentifierUtils.normalizePhone(request.phoneNumber()));
        String oldKey = null;
        if (photo != null && !photo.isEmpty()) {
            oldKey = person.getPhotoKey();
            person.setPhotoKey(storageService.uploadImage(photo, "pickup-persons/" + person.getChild().getId()));
        }
        resetReview(person, PickupStatus.PENDING, null, null);
        storageService.delete(oldKey);
        return toResponse(person);
    }

    @Transactional
    public void delete(Long parentId, Long pickupPersonId) {
        PickupPerson person = findOwnPickupPerson(parentId, pickupPersonId);
        pickupPersonRepository.delete(person);
        storageService.delete(person.getPhotoKey());
    }

    @Transactional(readOnly = true)
    public List<PickupPersonResponse> listByStatus(PickupStatus status) {
        return pickupPersonRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PickupPersonResponse review(Long reviewerId, Long pickupPersonId, boolean approved, String reason) {
        PickupPerson person = pickupPersonRepository.findById(pickupPersonId)
                .orElseThrow(() -> new AppException(ErrorCode.PICKUP_PERSON_NOT_FOUND));
        User reviewer = userRepository.getReferenceById(reviewerId);
        if (approved) {
            resetReview(person, PickupStatus.APPROVED, null, reviewer);
        } else {
            resetReview(person, PickupStatus.REJECTED, StringUtils.hasText(reason) ? reason.trim() : null, reviewer);
        }
        return toResponse(person);
    }

    @Transactional(readOnly = true)
    public List<PickupPersonResponse> listApproved(Long childId) {
        if (!childRepository.existsById(childId)) {
            throw new AppException(ErrorCode.CHILD_NOT_FOUND);
        }
        return pickupPersonRepository.findByChildIdAndStatusOrderByCreatedAtAsc(childId, PickupStatus.APPROVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PickupPersonResponse> listAll(Long childId) {
        return pickupPersonRepository.findByChildIdOrderByCreatedAtAsc(childId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * After the child moves to another parent, every pickup person needs a new approval.
     */
    @Transactional
    public void requireReapproval(Long childId) {
        pickupPersonRepository.findByChildIdOrderByCreatedAtAsc(childId)
                .forEach(person -> resetReview(person, PickupStatus.PENDING, null, null));
    }

    private static void resetReview(PickupPerson person, PickupStatus status, String reason, User reviewer) {
        person.setStatus(status);
        person.setRejectReason(reason);
        person.setReviewedBy(reviewer);
        person.setReviewedAt(reviewer == null ? null : Instant.now());
    }

    // A parent only sees their own children; other ids look like they do not exist.
    private Child findOwnChild(Long parentId, Long childId) {
        return childRepository.findByIdAndParentId(childId, parentId)
                .orElseThrow(() -> new AppException(ErrorCode.CHILD_NOT_FOUND));
    }

    private PickupPerson findOwnPickupPerson(Long parentId, Long pickupPersonId) {
        return pickupPersonRepository.findById(pickupPersonId)
                .filter(person -> person.getChild().getParent().getId().equals(parentId))
                .orElseThrow(() -> new AppException(ErrorCode.PICKUP_PERSON_NOT_FOUND));
    }

    private PickupPersonResponse toResponse(PickupPerson person) {
        Child child = person.getChild();
        return new PickupPersonResponse(
                person.getId(),
                child.getId(),
                child.getFullName(),
                person.getFullName(),
                person.getRelationship(),
                person.getPhoneNumber(),
                storageService.url(person.getPhotoKey()),
                person.getStatus(),
                person.getRejectReason(),
                person.getReviewedAt());
    }
}
