package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollmentPhotos;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.ChildPhotoStorage;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildPhotoService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ImageFiles;
import com.carenest.utils.enums.Permission;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChildPhotoServiceImpl implements ChildPhotoService {
    private static final String LOG_PREFIX = "[ChildPhotoServiceImpl]";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final ChildRepository childRepository;
    private final ChildGuardianRepository guardianRepository;
    private final ChildPhotoStorage storage;
    private final ChildIntakeService intakeService;
    private final ChildAccessPolicy childAccess;
    private final CampusAccessPolicy campusAccess;
    private final StaffAccessPolicy staffAccess;
    private final Clock clock;

    @Override
    public EnrollmentPhotos validatePhotos(int guardianCount, Map<String, List<MultipartFile>> files) {
        String logPrefix = LOG_PREFIX + "|validatePhotos";
        log.info("{}|START", logPrefix);
        try {
            ImageContent child = null;
            Map<Integer, ImageContent> guardians = new HashMap<>();
            for (var entry : files.entrySet()) {
                String name = entry.getKey();
                List<MultipartFile> values = entry.getValue();
                String field = "photo";
                int index = -1;
                if (name.equals("payload")) {
                    if (values.size() != 1) invalid(field, "Chỉ gửi một phần thông tin tiếp nhận");
                    continue;
                }
                if (name.matches("guardianPhoto[0-2]")) {
                    index = name.charAt(name.length() - 1) - '0';
                    field = "guardians." + index + ".photo";
                    if (index >= guardianCount) invalid(field, "Ảnh không thuộc phụ huynh trên hồ sơ");
                } else if (!name.equals("childPhoto")) {
                    invalid(field, "Phần tệp tải lên không hợp lệ");
                }
                if (values.size() != 1) invalid(field, "Chỉ chọn một ảnh cho mỗi người");
                ImageContent image = inspect(values.getFirst(), field);
                if (index < 0) child = image;
                else guardians.put(index, image);
            }
            EnrollmentPhotos photos = new EnrollmentPhotos(child, guardians);
            log.info("{}|END", logPrefix);
            return photos;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachToEnrollment(Long childId, EnrollmentPhotos photos) {
        String logPrefix = LOG_PREFIX + "|attachToEnrollment|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            if (photos.child() != null) {
                Child child = childRepository
                        .findById(childId)
                        .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_FOUND));
                ImageContent image = photos.child();
                child.assignEnrollmentPhoto(store(image), image.contentType(), image.content().length, clock.instant());
                childRepository.save(child);
            }
            if (!photos.guardians().isEmpty()) {
                List<ChildGuardian> guardians = guardianRepository.findAllByChildIdOrderByPositionAsc(childId);
                for (var entry : photos.guardians().entrySet()) {
                    ChildGuardian guardian = guardians.stream()
                            .filter(g -> g.getPosition() == entry.getKey())
                            .findFirst()
                            .orElseThrow(() -> GlobalException.of(ApiCode.INVALID_REQUEST_DATA));
                    ImageContent image = entry.getValue();
                    guardian.assignEnrollmentPhoto(
                            store(image), image.contentType(), image.content().length, clock.instant());
                    guardianRepository.save(guardian);
                }
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getChildPhoto(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|getChildPhoto|childId=" + childId + "|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            readableProfile(actor, childId);
            Child child = childRepository
                    .findById(childId)
                    .filter(c -> c.getPhotoKey() != null)
                    .orElseThrow(() -> GlobalException.of(ApiCode.FILE_NOT_FOUND));
            ImageContent result = new ImageContent(storage.load(child.getPhotoKey()), child.getPhotoContentType());
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getGuardianPhoto(CurrentUser actor, Long childId, Long guardianId) {
        String logPrefix = LOG_PREFIX + "|getGuardianPhoto|childId=" + childId + "|guardianId=" + guardianId
                + "|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            readableProfile(actor, childId);
            ChildGuardian guardian = guardianRepository
                    .findById(guardianId)
                    .filter(g -> childId.equals(g.getChildId()) && g.getPhotoKey() != null)
                    .orElseThrow(() -> GlobalException.of(ApiCode.FILE_NOT_FOUND));
            ImageContent result =
                    new ImageContent(storage.load(guardian.getPhotoKey()), guardian.getPhotoContentType());
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChildViewResponse getProfile(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|getProfile|childId=" + childId + "|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            ChildViewResponse view = readableProfile(actor, childId);
            boolean manageAccounts = actor.hasPermission(Permission.PARENT_ACCOUNT_MANAGE)
                    && (staffAccess.managesWholeSchool(actor)
                            || (view.campusId() != null
                                    && staffAccess.managedCampusIds(actor).contains(view.campusId())));
            if (!manageAccounts) view = view.withoutGuardianAccounts();
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private ChildViewResponse readableProfile(CurrentUser actor, Long childId) {
        boolean classroomScope = childAccess.canReadChild(actor, childId);
        boolean profilePermission =
                actor.hasPermission(Permission.CHILD_PROFILE_VIEW) || actor.hasPermission(Permission.CHILD_ENROLL);
        boolean campusScope = profilePermission && campusAccess.canReadAllCampuses(actor);
        if (!classroomScope && !campusScope) throw GlobalException.of(ApiCode.FILE_NOT_FOUND);
        ChildViewResponse view = intakeService.getChildView(childId);
        return view;
    }

    private String store(ImageContent image) {
        String key = storage.store(image.content(), image.contentType());
        // Cùng transaction với hồ sơ: dọn object mới nếu DB/ảnh sau đó lỗi, kể cả lỗi lúc commit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.removeQuietly(key);
            }
        });
        return key;
    }

    private static ImageContent inspect(MultipartFile file, String field) {
        if (file == null || file.isEmpty()) invalid(field, "Chọn tệp ảnh có nội dung");
        if (file.getSize() > MAX_BYTES) invalid(field, "Ảnh tối đa 2 MB");
        try {
            byte[] content = file.getBytes();
            if (content.length == 0 || content.length > MAX_BYTES) invalid(field, "Ảnh tối đa 2 MB");
            String type = ImageFiles.sniff(content);
            if (type == null || !validImage(content, type)) invalid(field, "Chọn ảnh JPG, PNG hoặc WEBP hợp lệ");
            return new ImageContent(content, type);
        } catch (IOException ex) {
            invalid(field, "Không đọc được ảnh, hãy chọn lại tệp");
            throw new IllegalStateException(ex);
        }
    }

    private static boolean validImage(byte[] content, String type) throws IOException {
        if (type.equals("image/webp")) {
            // JDK không có decoder WEBP: kiểm RIFF và frame thật, không nhận riêng header VP8X.
            if (content.length < 30 || littleEndian(content, 4) != content.length - 8) return false;
            return webpFrames(content, 12, content.length, false);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 20_000_000) return false;
                BufferedImage image = reader.read(0);
                return image != null;
            } finally {
                reader.dispose();
            }
        }
    }

    private static long littleEndian(byte[] content, int offset) {
        return (content[offset] & 255L)
                | ((content[offset + 1] & 255L) << 8)
                | ((content[offset + 2] & 255L) << 16)
                | ((content[offset + 3] & 255L) << 24);
    }

    private static boolean webpFrames(byte[] bytes, int start, int end, boolean nested) {
        boolean frame = false;
        int offset = start;
        while (offset + 8 <= end) {
            String type = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long length = littleEndian(bytes, offset + 4);
            int data = offset + 8;
            if (length > end - data) return false;
            int size = (int) length;
            if (type.equals("VP8 ")) {
                if (size <= 10
                        || (bytes[data] & 1) != 0
                        || (bytes[data + 3] & 255) != 0x9d
                        || bytes[data + 4] != 1
                        || bytes[data + 5] != 0x2a) return false;
                int width = ((bytes[data + 6] & 255) | ((bytes[data + 7] & 255) << 8)) & 0x3fff;
                int height = ((bytes[data + 8] & 255) | ((bytes[data + 9] & 255) << 8)) & 0x3fff;
                if (!portraitDimensions(width, height)) return false;
                frame = true;
            } else if (type.equals("VP8L")) {
                if (size <= 5 || bytes[data] != 0x2f || (bytes[data + 4] & 0xe0) != 0) return false;
                int width = 1 + ((bytes[data + 1] & 255) | ((bytes[data + 2] & 0x3f) << 8));
                int height = 1
                        + (((bytes[data + 2] & 0xc0) >> 6)
                                | ((bytes[data + 3] & 255) << 2)
                                | ((bytes[data + 4] & 15) << 10));
                if (!portraitDimensions(width, height)) return false;
                frame = true;
            } else if (type.equals("VP8X")) {
                if (size != 10) return false;
            } else if (type.equals("ANMF")) {
                if (nested || size <= 16 || !webpFrames(bytes, data + 16, data + size, true)) return false;
                frame = true;
            }
            offset = data + size + (size & 1);
        }
        return frame && offset == end;
    }

    private static boolean portraitDimensions(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= 20_000_000;
    }

    private static void invalid(String field, String message) {
        throw GlobalException.withFieldErrors(
                ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse(field, message)));
    }
}
