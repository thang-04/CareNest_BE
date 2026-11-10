package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.MenuTemplateRequest;
import com.carenest.dto.meals.MenuTemplateResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.MenuTemplateService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Bộ thực đơn mẫu 4 tuần theo khối (NUT-13): lưu cả lưới, kiểm bữa của khối và món đang dùng, quyền MENU_MANAGE. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class MenuTemplateIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MenuTemplateService menuTemplateService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vp;
    private long com;
    private long canh;

    @BeforeEach
    void setUp() {
        com = dish("COM", "Cơm", "CARB");
        canh = dish("CANH", "Canh bí", "SOUP");
        principal = user("principal", CareNestRole.PRINCIPAL);
        vp = user("vp", CareNestRole.VICE_PRINCIPAL);
    }

    @Test
    @DisplayName("NUT-13: tạo bộ 4 tuần cho mẫu giáo, đọc lại đủ lưới; sửa thay cả lưới kể cả ô trùng khóa cũ")
    void createReadAndReplaceGrid() {
        MenuTemplateResponse created = menuTemplateService.createTemplate(
                principal,
                request(
                        "Bộ mùa đông",
                        List.of(
                                item(1, 1, MealSession.LUNCH, com),
                                item(1, 1, MealSession.LUNCH, canh),
                                item(4, 5, MealSession.AFTERNOON, canh),
                                item(1, 1, MealSession.LUNCH, com))));
        assertThat(created.block()).isEqualTo(MenuBlock.KINDERGARTEN);
        assertThat(created.sessions()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(created.ageGroupIds()).hasSize(3);
        assertThat(created.dishCount()).isEqualTo(2);
        assertThat(created.canEdit()).isTrue();
        assertThat(created.items()).hasSize(3);
        entityManager.flush();
        entityManager.clear();

        MenuTemplateResponse read = menuTemplateService.getTemplate(vp, created.id());
        assertThat(read.canEdit()).isFalse();
        assertThat(read.items())
                .extracting(
                        MenuTemplateResponse.Item::weekNo,
                        MenuTemplateResponse.Item::weekday,
                        MenuTemplateResponse.Item::dishName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 1, "Cơm"),
                        org.assertj.core.groups.Tuple.tuple(1, 1, "Canh bí"),
                        org.assertj.core.groups.Tuple.tuple(4, 5, "Canh bí"));

        // Giữ ô (tuần 1, thứ 2, trưa, cơm) và đổi phần còn lại ⇒ không vướng khóa duy nhất khi thay lưới
        MenuTemplateResponse updated = menuTemplateService.updateTemplate(
                principal,
                created.id(),
                request(
                        "Bộ mùa đông",
                        List.of(item(1, 1, MealSession.LUNCH, com), item(2, 3, MealSession.LUNCH, canh))));
        entityManager.flush();
        assertThat(updated.items()).hasSize(2);
        assertThat(menuTemplateService.getTemplates(vp, MenuBlock.KINDERGARTEN, CatalogStatus.ACTIVE))
                .singleElement()
                .satisfies(t -> assertThat(t.dishCount()).isEqualTo(2));
    }

    @Test
    @DisplayName("Bữa khối không phục vụ, món ngừng dùng bị từ chối; PHT chưa được cấp quyền không lưu được")
    void rejectsInvalidCellsAndMissingPermission() {
        expectCode(
                () -> menuTemplateService.createTemplate(
                        principal, request("Bộ A", List.of(item(1, 1, MealSession.MORNING, com)))),
                ApiCode.MEAL_PLAN_SESSION_INVALID);
        jdbc.update("UPDATE dishes SET status = 'INACTIVE' WHERE id = ?", canh);
        expectCode(
                () -> menuTemplateService.createTemplate(
                        principal, request("Bộ A", List.of(item(1, 1, MealSession.LUNCH, canh)))),
                ApiCode.MEAL_PLAN_DISH_INACTIVE);
        expectCode(
                () -> menuTemplateService.createTemplate(
                        vp, request("Bộ A", List.of(item(1, 1, MealSession.LUNCH, com)))),
                ApiCode.FORBIDDEN);
    }

    private static MenuTemplateRequest request(String name, List<MenuTemplateRequest.Item> items) {
        return new MenuTemplateRequest(name, "Mùa đông", MenuBlock.KINDERGARTEN, items);
    }

    private static MenuTemplateRequest.Item item(int week, int weekday, MealSession session, long dishId) {
        return new MenuTemplateRequest.Item(week, weekday, session, dishId);
    }

    private long dish(String code, String name, String type) {
        return jdbc.queryForObject(
                "INSERT INTO dishes (code, name, dish_type) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                code,
                name,
                type);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "kc-" + username,
                username,
                username);
        return new CurrentUser(
                id, "kc-" + username, username, username, null, Set.of(role), TestPermissions.defaultsOf(role));
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }
}
