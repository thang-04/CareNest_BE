package com.carenest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.security.TestPermissions;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mọi endpoint thật phải khai báo role bằng @PreAuthorize (lớp 1); thiếu là lỗi build chứ không
 * âm thầm mở cho mọi user đã đăng nhập.
 */
class ControllerSecurityCoverageTest {

    private static final String CONTROLLER_PACKAGE = "com.carenest.controller";
    // Trong SpEL dùng @permissionGuard, mọi chuỗi trong nháy đơn là mã quyền
    private static final Pattern PERMISSION_CODE = Pattern.compile("'([^']+)'");

    @Test
    void everyHandlerMethodDeclaresPreAuthorize() throws ClassNotFoundException {
        List<Class<?>> controllers = findProductionControllers();
        List<String> unprotected = new ArrayList<>();

        for (Class<?> controller : controllers) {
            boolean classProtected = AnnotatedElementUtils.hasAnnotation(controller, PreAuthorize.class);
            for (Method method : controller.getDeclaredMethods()) {
                boolean isHandler = AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
                if (isHandler && !classProtected && !AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)) {
                    unprotected.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(controllers).as("phải quét được controller thật").isNotEmpty();
        assertThat(unprotected).as("endpoint thiếu @PreAuthorize").isEmpty();
    }

    // Controller trong src/test (ResponseContractTestController...) chỉ để test hạ tầng, bỏ qua
    private static List<Class<?>> findProductionControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> type = ClassUtils.forName(definition.getBeanClassName(), null);
            String location =
                    type.getProtectionDomain().getCodeSource().getLocation().toString();
            if (!location.contains("test-classes")) {
                controllers.add(type);
            }
        }
        return controllers;
    }

    @Test
    @DisplayName("AUTH-07: mọi mã quyền dùng trong @PreAuthorize phải có trong enum Permission (gõ sai = từ chối ngầm)")
    void everyPermissionCodeInPreAuthorizeExists() throws ClassNotFoundException {
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, String> handler : preAuthorizeByHandler().entrySet()) {
            if (!handler.getValue().contains("@permissionGuard")) {
                continue;
            }
            Matcher matcher = PERMISSION_CODE.matcher(handler.getValue());
            while (matcher.find()) {
                if (Permission.fromCode(matcher.group(1)).isEmpty()) {
                    unknown.add(handler.getKey() + " → " + matcher.group(1));
                }
            }
        }
        assertThat(unknown).as("mã quyền không tồn tại").isEmpty();
    }

    // Quyền mặc định phải cho ra đúng tập role của hasRole trước khi chuyển sang guard (không đổi hành vi)
    @Test
    @DisplayName("AUTH-07: quyền mặc định giữ nguyên tập role được gọi từng endpoint như hasRole cũ")
    void defaultPermissionsKeepPreviousRoleAccess() throws ClassNotFoundException {
        Map<String, Set<CareNestRole>> expected = Map.of(
                "ChildController#getChildProfile",
                        EnumSet.of(
                                CareNestRole.TEACHER,
                                CareNestRole.VICE_PRINCIPAL,
                                CareNestRole.PRINCIPAL,
                                CareNestRole.PARENT),
                // AUTH-06 (team chốt 2026-10-16): HT cấp/khóa tài khoản nghiệp vụ — đổi có chủ đích so với
                // hasRole(ADMIN)
                "AccountController#createAccount", EnumSet.of(CareNestRole.ADMIN, CareNestRole.PRINCIPAL),
                "AccountController#updateAccountStatus", EnumSet.of(CareNestRole.ADMIN, CareNestRole.PRINCIPAL));
        Map<String, String> handlers = preAuthorizeByHandler();

        expected.forEach((handler, roles) -> {
            Matcher matcher = PERMISSION_CODE.matcher(handlers.get(handler));
            assertThat(matcher.find()).as(handler).isTrue();
            Permission permission = Permission.valueOf(matcher.group(1));
            Set<CareNestRole> allowed = EnumSet.noneOf(CareNestRole.class);
            for (CareNestRole role : CareNestRole.values()) {
                if (TestPermissions.defaultsOf(role).contains(permission)) {
                    allowed.add(role);
                }
            }
            assertThat(allowed).as(handler).isEqualTo(roles);
        });
    }

    private static Map<String, String> preAuthorizeByHandler() throws ClassNotFoundException {
        Map<String, String> result = new HashMap<>();
        for (Class<?> controller : findProductionControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (preAuthorize != null) {
                    result.put(controller.getSimpleName() + "#" + method.getName(), preAuthorize.value());
                }
            }
        }
        return result;
    }

    @Test
    void scannerSeesKnownControllers() throws ClassNotFoundException {
        assertThat(findProductionControllers())
                .extracting(Class::getSimpleName)
                .contains("AccountController", "ChildController")
                .doesNotContain("ResponseContractTestController", "SecurityTestController");
    }
}
