package com.carenest.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.carenest.utils.ResponseJson;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ranh giới kiến trúc tự kiểm (ADR-0001, ADR-0012, guide mục 4–5, MODULE_MAP).
 * allowEmptyShould/withOptionalLayers: main mới có scaffold, các lớp feature có thể chưa có class.
 */
@AnalyzeClasses(packages = "com.carenest", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final String BASE = "com.carenest.";

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Controller")
            .definedBy(BASE + "controller..")
            .layer("Service")
            .definedBy(BASE + "service..")
            .layer("Repository")
            .definedBy(BASE + "repository..")
            .whereLayer("Controller")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Service")
            .mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Repository")
            .mayOnlyBeAccessedByLayers("Service")
            .because("guide mục 4: controller → service → repository");

    // Policy trong security.authorization đi qua service; riêng package security (CurrentUserService) đọc tài khoản
    @ArchTest
    static final ArchRule repositoriesOnlyUsedByServices = classes()
            .that()
            .resideInAPackage(BASE + "repository..")
            .should()
            .onlyHaveDependentClassesThat()
            .resideInAnyPackage(BASE + "service..", BASE + "repository..", BASE + "security")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule serviceUsesOnlyOwnFeatureRepositories = classes()
            .that()
            .resideInAPackage(BASE + "service..")
            .should(onlyDependOnRepositoriesOfOwnFeature())
            .allowEmptyShould(true)
            .because("ADR-0001: cần dữ liệu feature khác ⇒ gọi service của feature đó");

    @ArchTest
    static final ArchRule serviceFeaturesFreeOfCycles =
            slices().matching(BASE + "service.(*)..").should().beFreeOfCycles().allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesDoNotBuildHttpResponses = noClasses()
            .that()
            .resideInAPackage(BASE + "service..")
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(ResponseJson.class, ResponseEntity.class, HttpStatus.class, HttpStatusCode.class)
            .allowEmptyShould(true)
            .because("guide mục 5: service ném GlobalException(ApiCode, desc)");

    // Enum nằm trong entity/ (vd. UserStatus) được dùng trong DTO; chỉ cấm class @Entity
    @ArchTest
    static final ArchRule apiDoesNotExposeEntities = noClasses()
            .that()
            .resideInAnyPackage(BASE + "controller..", BASE + "dto..")
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith(Entity.class)
            .allowEmptyShould(true)
            .because("guide mục 5: không trả entity, không dùng entity làm request body");

    @ArchTest
    static final ArchRule controllersInControllerPackage = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage(BASE + "controller..")
            .allowEmptyShould(true)
            .because("guide mục 7: prefix API chỉ gắn cho com.carenest.controller");

    @ArchTest
    static final ArchRule externalCallsOnlyInIntegration = noClasses()
            .that()
            .resideOutsideOfPackage(BASE + "integration..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web.client..",
                    "org.springframework.http.client..",
                    "org.springframework.web.reactive.function.client..",
                    "java.net.http..",
                    "software.amazon.awssdk..",
                    "com.google.firebase..",
                    "com.google.genai..",
                    "com.openai..")
            .because("guide mục 4, NFR-MAINT-03: dịch vụ ngoài chỉ qua integration/");

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    private static ArchCondition<JavaClass> onlyDependOnRepositoriesOfOwnFeature() {
        return new ArchCondition<>("only depend on repositories of the same feature") {
            @Override
            public void check(JavaClass service, ConditionEvents events) {
                String feature = featureOf(service.getPackageName(), BASE + "service");
                for (Dependency dependency : service.getDirectDependenciesFromSelf()) {
                    String target = dependency.getTargetClass().getPackageName();
                    if (target.startsWith(BASE + "repository.")
                            && !featureOf(target, BASE + "repository").equals(feature)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    // "com.carenest.service.child.x" → "child"; class nằm thẳng trong service/ → ""
    private static String featureOf(String packageName, String layerPackage) {
        if (packageName.length() <= layerPackage.length()) {
            return "";
        }
        String rest = packageName.substring(layerPackage.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
