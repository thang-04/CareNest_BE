package com.carenest.service.notification;

import com.carenest.utils.enums.PushState;

public interface PushDeliveryService {
    PushState pushOne(Long notificationId);
}
