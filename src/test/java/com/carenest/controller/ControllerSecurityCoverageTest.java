package com.carenest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
    void scannerSeesKnownControllers() throws ClassNotFoundException {
        assertThat(findProductionControllers())
                .extracting(Class::getSimpleName)
                .contains("AccountController", "ChildController")
                .doesNotContain("ResponseContractTestController", "SecurityTestController");
    }
}
