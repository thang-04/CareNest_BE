package com.carenest.service;

import com.carenest.dto.request.UpdateProfileRequest;
import com.carenest.dto.response.ImageResponse;
import com.carenest.dto.response.UserResponse;
import com.carenest.entity.User;
import com.carenest.entity.UserImage;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.UserImageRepository;
import com.carenest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Own profile, avatar and extra images of any user, whatever the role.
 */
@Service
@RequiredArgsConstructor
public class UserImageService {

    private final UserRepository userRepository;
    private final UserImageRepository userImageRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = findUser(userId);
        return UserResponse.from(user, storageService.url(user.getAvatar()));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setFullName(request.fullName().trim());
        return UserResponse.from(user, storageService.url(user.getAvatar()));
    }

    @Transactional
    public UserResponse updateAvatar(Long userId, MultipartFile file) {
        User user = findUser(userId);
        String oldKey = user.getAvatar();
        user.setAvatar(storageService.uploadImage(file, "avatars/" + userId));
        storageService.delete(oldKey);
        return UserResponse.from(user, storageService.url(user.getAvatar()));
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> listImages(Long userId) {
        findUser(userId);
        return userImageRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ImageResponse addImage(Long userId, MultipartFile file, String title) {
        User user = findUser(userId);
        String key = storageService.uploadImage(file, "users/" + userId);
        UserImage image = userImageRepository.save(UserImage.builder()
                .user(user)
                .objectKey(key)
                .title(StringUtils.hasText(title) ? title.trim() : null)
                .build());
        return toResponse(image);
    }

    @Transactional
    public void deleteImage(Long userId, Long imageId) {
        UserImage image = userImageRepository.findByIdAndUserId(imageId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.IMAGE_NOT_FOUND));
        userImageRepository.delete(image);
        storageService.delete(image.getObjectKey());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private ImageResponse toResponse(UserImage image) {
        return new ImageResponse(image.getId(), image.getTitle(),
                storageService.url(image.getObjectKey()), image.getCreatedAt());
    }
}
