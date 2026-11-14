package com.carenest.controller.notification;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.notification.NotificationResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.NotificationType;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Mở một thông báo từ banner (FR-007): của mình trả dữ liệu, của người khác 404. */
@WebMvcTest(NotificationController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private NotificationService notificationService;

    private CurrentUser parent() {
        CurrentUser user = new CurrentUser(7L, "kc-7", "parent", "Demo", null, Set.of(CareNestRole.PARENT), Set.of());
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    @Test
    void ownNotificationIsReturned() throws Exception {
        CurrentUser user = parent();
        given(notificationService.getMyNotification(eq(user), eq(11L)))
                .willReturn(new NotificationResponse(
                        11L,
                        NotificationType.CHILD_PICKED_UP,
                        "Bé đã được đón",
                        "Nội dung",
                        3L,
                        Instant.parse("2026-11-15T09:00:00Z"),
                        null,
                        null));

        mockMvc.perform(get("/api/me/notifications/11")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CareNestRole.PARENT.authority()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.type").value("CHILD_PICKED_UP"));
    }

    @Test
    void someoneElsesNotificationIsNotFound() throws Exception {
        CurrentUser user = parent();
        given(notificationService.getMyNotification(eq(user), eq(12L)))
                .willThrow(GlobalException.of(ApiCode.NOTIFICATION_NOT_FOUND));

        mockMvc.perform(get("/api/me/notifications/12")
                        .with(jwt().authorities(new SimpleGrantedAuthority(CareNestRole.PARENT.authority()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
