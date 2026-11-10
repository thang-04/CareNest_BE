package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MenuTemplateRequest;
import com.carenest.dto.meals.MenuTemplateResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.MenuTemplateItem;
import com.carenest.entity.meals.MenuTemplateSet;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MenuTemplateSetRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MenuTemplateService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MenuTemplateServiceImpl implements MenuTemplateService {
    private static final String LOG_PREFIX = "[MenuTemplateServiceImpl]";

    private final MenuTemplateSetRepository templateRepository;
    private final DishRepository dishRepository;
    private final AgeGroupService ageGroupService;

    @Override
    @Transactional(readOnly = true)
    public List<MenuTemplateResponse> getTemplates(CurrentUser currentUser, MenuBlock block, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|getTemplates|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            Map<MenuBlock, List<MealSession>> served = servedSessions(ageGroupService.getAgeGroups());
            Map<MenuBlock, List<Long>> groups = ageGroupIdsByBlock(ageGroupService.getAgeGroups());
            List<MenuTemplateSet> sets = templateRepository.findAllByOrderByMenuBlockAscNameAsc().stream()
                    .filter(set -> block == null || set.getMenuBlock() == block)
                    .filter(set -> status == null || set.getStatus() == status)
                    .toList();
            Map<Long, Dish> dishes = dishesOf(sets);
            List<MenuTemplateResponse> response = sets.stream()
                    .map(set -> toResponse(currentUser, set, served, dishes, groups))
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MenuTemplateResponse getTemplate(CurrentUser currentUser, Long templateId) {
        String logPrefix = LOG_PREFIX + "|getTemplate|userId=" + currentUser.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        try {
            MenuTemplateSet set = findTemplate(templateId);
            MenuTemplateResponse response = toResponse(
                    currentUser,
                    set,
                    servedSessions(ageGroupService.getAgeGroups()),
                    dishesOf(List.of(set)),
                    ageGroupIdsByBlock(ageGroupService.getAgeGroups()));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MenuTemplateResponse createTemplate(CurrentUser currentUser, MenuTemplateRequest request) {
        String logPrefix = LOG_PREFIX + "|createTemplate|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            Map<MenuBlock, List<MealSession>> served = servedSessions(ageGroupService.getAgeGroups());
            List<MenuTemplateItem> items = toItems(request, served);
            MenuTemplateSet set = MenuTemplateSet.create(currentUser.userId());
            set.changeInfo(request.name().trim(), textOf(request.seasonLabel()), request.block(), currentUser.userId());
            set.replaceItems(items);
            set = templateRepository.save(set);
            log.info("{}|templateId={}|END", logPrefix, set.getId());
            return toResponse(
                    currentUser,
                    set,
                    served,
                    dishesOf(List.of(set)),
                    ageGroupIdsByBlock(ageGroupService.getAgeGroups()));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MenuTemplateResponse updateTemplate(CurrentUser currentUser, Long templateId, MenuTemplateRequest request) {
        String logPrefix = LOG_PREFIX + "|updateTemplate|userId=" + currentUser.userId() + "|templateId=" + templateId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuTemplateSet set = findTemplate(templateId);
            Map<MenuBlock, List<MealSession>> served = servedSessions(ageGroupService.getAgeGroups());
            List<MenuTemplateItem> items = toItems(request, served);
            set.changeInfo(request.name().trim(), textOf(request.seasonLabel()), request.block(), currentUser.userId());
            // Hibernate chèn trước khi xóa ⇒ xóa lưới cũ và flush trước, tránh trùng khóa (bộ, tuần, ngày, bữa, món)
            set.replaceItems(List.of());
            templateRepository.flush();
            set.replaceItems(items);
            templateRepository.flush();
            log.info("{}|END", logPrefix);
            return toResponse(
                    currentUser,
                    set,
                    served,
                    dishesOf(List.of(set)),
                    ageGroupIdsByBlock(ageGroupService.getAgeGroups()));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MenuTemplateResponse changeTemplateStatus(CurrentUser currentUser, Long templateId, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|changeTemplateStatus|userId=" + currentUser.userId() + "|templateId="
                + templateId + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuTemplateSet set = findTemplate(templateId);
            set.changeStatus(status, currentUser.userId());
            log.info("{}|END", logPrefix);
            return toResponse(
                    currentUser,
                    set,
                    servedSessions(ageGroupService.getAgeGroups()),
                    dishesOf(List.of(set)),
                    ageGroupIdsByBlock(ageGroupService.getAgeGroups()));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private MenuTemplateSet findTemplate(Long templateId) {
        return templateRepository
                .findWithItemsById(templateId)
                .orElseThrow(() -> GlobalException.of(ApiCode.MENU_TEMPLATE_NOT_FOUND));
    }

    // NUT-13: bữa phải là bữa khối đang phục vụ; món phải đang dùng (cùng kiểm tra với kế hoạch tuần)
    private List<MenuTemplateItem> toItems(MenuTemplateRequest request, Map<MenuBlock, List<MealSession>> served) {
        List<MealSession> sessions = served.getOrDefault(request.block(), List.of());
        Set<Long> dishIds =
                request.items().stream().map(MenuTemplateRequest.Item::dishId).collect(Collectors.toSet());
        Map<Long, Dish> dishes = dishRepository.findAllById(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<MenuTemplateItem> items = new ArrayList<>();
        Map<String, Integer> order = new HashMap<>();
        for (MenuTemplateRequest.Item item : request.items()) {
            if (!sessions.contains(item.session())) {
                throw GlobalException.of(
                        ApiCode.MEAL_PLAN_SESSION_INVALID, blockLabel(request.block()), item.session());
            }
            Dish dish = dishes.get(item.dishId());
            if (dish == null) {
                throw GlobalException.of(ApiCode.DISH_NOT_FOUND);
            }
            if (dish.getStatus() != CatalogStatus.ACTIVE) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_DISH_INACTIVE, dish.getName());
            }
            String cell = item.weekNo() + "|" + item.weekday() + "|" + item.session();
            if (seen.add(cell + "|" + item.dishId())) {
                int sort = order.merge(cell, 1, Integer::sum) - 1;
                items.add(MenuTemplateItem.of(item.weekNo(), item.weekday(), item.session(), item.dishId(), sort));
            }
        }
        return items;
    }

    private Map<Long, Dish> dishesOf(Collection<MenuTemplateSet> sets) {
        Set<Long> ids = sets.stream()
                .flatMap(set -> set.getItems().stream())
                .map(MenuTemplateItem::getDishId)
                .collect(Collectors.toSet());
        return dishRepository.findAllById(ids).stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    private static MenuTemplateResponse toResponse(
            CurrentUser currentUser,
            MenuTemplateSet set,
            Map<MenuBlock, List<MealSession>> served,
            Map<Long, Dish> dishes,
            Map<MenuBlock, List<Long>> ageGroupIds) {
        List<MenuTemplateResponse.Item> items = set.getItems().stream()
                .map(item -> {
                    Dish dish = dishes.get(item.getDishId());
                    return new MenuTemplateResponse.Item(
                            item.getWeekNo(),
                            item.getWeekday(),
                            item.getSession(),
                            item.getDishId(),
                            dish == null ? null : dish.getName(),
                            dish == null ? null : dish.getDishType());
                })
                .toList();
        int dishCount = (int) set.getItems().stream()
                .map(MenuTemplateItem::getDishId)
                .distinct()
                .count();
        return new MenuTemplateResponse(
                set.getId(),
                set.getName(),
                set.getSeasonLabel(),
                set.getMenuBlock(),
                set.getStatus(),
                served.getOrDefault(set.getMenuBlock(), List.of()),
                ageGroupIds.getOrDefault(set.getMenuBlock(), List.of()),
                dishCount,
                set.getUpdatedAt(),
                currentUser.hasPermission(Permission.MENU_MANAGE),
                items);
    }

    // Bữa khối phục vụ = hợp các bữa của nhóm tuổi thuộc khối (cấu hình trường)
    private static Map<MenuBlock, List<MealSession>> servedSessions(Collection<AgeGroupDetailResponse> ageGroups) {
        Map<MenuBlock, Set<MealSession>> served = new EnumMap<>(MenuBlock.class);
        ageGroups.stream()
                .filter(g -> g.mealSessions() != null)
                .forEach(g -> served.computeIfAbsent(MenuBlock.ofAgeGroup(g.ageToMonths()), b -> new TreeSet<>())
                        .addAll(g.mealSessions()));
        Map<MenuBlock, List<MealSession>> result = new EnumMap<>(MenuBlock.class);
        served.forEach((block, sessions) -> result.put(block, List.copyOf(sessions)));
        return result;
    }

    // Nhóm tuổi của từng khối (NUT-13), để màn thống kê chọn nhóm tuổi của bộ mẫu
    private static Map<MenuBlock, List<Long>> ageGroupIdsByBlock(Collection<AgeGroupDetailResponse> ageGroups) {
        Map<MenuBlock, List<Long>> result = new EnumMap<>(MenuBlock.class);
        ageGroups.forEach(g -> result.computeIfAbsent(MenuBlock.ofAgeGroup(g.ageToMonths()), b -> new ArrayList<>())
                .add(g.id()));
        return result;
    }

    // AUTH-20, NUT-13: bộ mẫu thuộc quyền lập thực đơn
    private static void checkCanManage(CurrentUser currentUser) {
        if (!currentUser.hasPermission(Permission.MENU_MANAGE)) {
            throw GlobalException.of(ApiCode.FORBIDDEN);
        }
    }

    private static String blockLabel(MenuBlock block) {
        return block == MenuBlock.NURSERY ? "Nhà trẻ" : "Mẫu giáo";
    }

    private static String textOf(String value) {
        return value == null ? "" : value.trim();
    }
}
