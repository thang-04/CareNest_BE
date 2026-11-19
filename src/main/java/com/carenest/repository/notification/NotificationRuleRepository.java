package com.carenest.repository.notification;

import com.carenest.entity.notification.NotificationRule;
import com.carenest.utils.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, NotificationType> {}
