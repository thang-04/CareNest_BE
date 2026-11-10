package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.utils.Constants.Security;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PermissionGuardTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<CurrentUserService> provider = mock(ObjectProvider.class);

    private final PermissionGuard guard = new PermissionGuard(provider);

    @BeforeEach
    void bindRequestWithTeacher() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                Security.CURRENT_USER_ATTRIBUTE,
                new CurrentUser(
                        7L,
                        "kc-7",
                        "t",
                        "T",
                        null,
                        Set.of(CareNestRole.TEACHER),
                        Set.of(Permission.CHILD_PROFILE_VIEW)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesPermissionsAlreadyResolvedForRequest() {
        assertThat(guard.has("CHILD_PROFILE_VIEW")).isTrue();
        assertThat(guard.has("PERMISSION_MANAGE")).isFalse();
        assertThat(guard.hasAny("PERMISSION_MANAGE", "CHILD_PROFILE_VIEW")).isTrue();
        verifyNoInteractions(provider);
    }

    @Test
    void unknownCodeFailsClosed() {
        assertThat(guard.has("child.view")).isFalse();
    }
}
