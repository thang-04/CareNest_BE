package com.carenest.dto.request;

import jakarta.validation.constraints.Size;

public record RejectPickupPersonRequest(
        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String reason
) {
}
