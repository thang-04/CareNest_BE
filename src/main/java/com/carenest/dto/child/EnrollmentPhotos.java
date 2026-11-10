package com.carenest.dto.child;

import com.carenest.dto.common.ImageContent;
import java.util.Map;

/** Tệp đã kiểm, index chỉ có nghĩa trong cùng một request tiếp nhận. */
public record EnrollmentPhotos(ImageContent child, Map<Integer, ImageContent> guardians) {
    public EnrollmentPhotos {
        guardians = Map.copyOf(guardians);
    }
}
