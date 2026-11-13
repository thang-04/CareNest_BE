package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.ResourceReviewRequest;
import com.carenest.dto.eduplan.ResourceShareRequest;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduResourceItem;
import com.carenest.entity.eduplan.EduResourceItemTag;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.eduplan.*;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.ResourceLibraryService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResourceLibraryServiceImplTest {
    private final EduResourceItemRepository items = mock(EduResourceItemRepository.class);
    private final EduResourceItemTagRepository tags = mock(EduResourceItemTagRepository.class);
    private final EduResourceAttachmentRepository attachments = mock(EduResourceAttachmentRepository.class);
    private final EduPlanGoalRepository goals = mock(EduPlanGoalRepository.class);
    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final CurrentUser teacher = user(7L, CareNestRole.TEACHER);
    private final CurrentUser leader = user(8L, CareNestRole.TEACHER);
    private final CurrentUser vice = user(9L, CareNestRole.VICE_PRINCIPAL);
    private ResourceLibraryServiceImpl service;

    private static CurrentUser user(long id, CareNestRole role) {
        return new CurrentUser(id, "kc-" + id, "u" + id, "U" + id, null, Set.of(role));
    }

    @BeforeEach
    void setUp() {
        service = new ResourceLibraryServiceImpl(
                items,
                tags,
                attachments,
                goals,
                mock(EduClassDocRepository.class),
                mock(EduClassDocService.class),
                assignments,
                storage,
                Clock.systemUTC(),
                notifications);
        when(assignments.findCurrentTeacherClassroomIds(any(), any())).thenReturn(Set.of(1L));
        when(assignments.findActiveTeamLeaderAgeGroupIds(any(), any())).thenReturn(Set.of());
        when(assignments.findActiveTeamLeaderAgeGroupIds(eq(8L), any())).thenReturn(Set.of(4L));
        when(items.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private EduResourceItem item(long id, long owner) {
        EduResourceItem i = EduResourceItem.link(owner, "Video " + id, 4L, "https://youtu.be/x" + id, Instant.now());
        ReflectionTestUtils.setField(i, "id", id);
        when(items.findById(id)).thenReturn(Optional.of(i));
        return i;
    }

    private static ResourceLibraryService.Query query(String view, List<Long> goalIds, Long themeId, String themeName) {
        return new ResourceLibraryService.Query(view, null, null, 4L, themeId, themeName, goalIds, null, null, 0, 50);
    }

    @Test
    void deleteBlockedWhileUsedInPlans() {
        item(5L, 7L);
        when(attachments.countByItemId(5L)).thenReturn(2L);
        // EDU-85: còn giáo án dùng thì chỉ ẩn được
        assertThatThrownBy(() -> service.delete(teacher, 5L))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_ITEM_IN_USE);
        verify(items, never()).delete(any());
    }

    @Test
    void deleteUnusedFileRemovesItemAndStoredFile() {
        EduResourceItem file =
                EduResourceItem.file(7L, "Tranh", 4L, "tranh.png", "image/png", 10, "hoclieu/k1", null, Instant.now());
        ReflectionTestUtils.setField(file, "id", 6L);
        when(items.findById(6L)).thenReturn(Optional.of(file));
        service.delete(teacher, 6L);
        verify(items).delete(file);
        verify(storage).removeQuietly(any(), eq("hoclieu/k1"));
    }

    @Test
    void othersCannotEditOrDeleteMyItem() {
        EduResourceItem i = item(5L, 7L);
        i.moveTo(EduResourceItem.SCHOOL, null, Instant.now());
        assertThatThrownBy(() -> service.delete(leader, 5L))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_ITEM_FORBIDDEN);
    }

    @Test
    void shareToClassOnlyForOwnClass() {
        item(5L, 7L);
        assertThatThrownBy(() -> service.share(teacher, 5L, new ResourceShareRequest("CLASS", 99L)))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_ITEM_FORBIDDEN);
        assertThat(service.share(teacher, 5L, new ResourceShareRequest("CLASS", 1L))
                        .scope())
                .isEqualTo("CLASS");
    }

    @Test
    void teacherRequestsSchoolLeaderReviewsVicePrincipalPublishesDirectly() {
        EduResourceItem i = item(5L, 7L);
        // EDU-84: giáo viên chỉ gửi đề nghị
        var pending = service.share(teacher, 5L, new ResourceShareRequest("SCHOOL", null));
        assertThat(pending.scope()).isEqualTo("MINE");
        assertThat(pending.shareStatus()).isEqualTo("PENDING");
        // Trả lại phải có lý do
        assertThatThrownBy(() -> service.review(leader, 5L, new ResourceReviewRequest(false, " ")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_REVIEW_REASON_REQUIRED);
        // Người tạo không tự duyệt
        assertThatThrownBy(() -> service.review(teacher, 5L, new ResourceReviewRequest(true, null)))
                .isInstanceOf(GlobalException.class);
        assertThat(service.review(leader, 5L, new ResourceReviewRequest(true, null))
                        .scope())
                .isEqualTo("SCHOOL");
        // EDU-84: người tạo nhận thông báo đã duyệt
        verify(notifications)
                .notifyUsers(
                        eq(List.of(7L)),
                        eq(NotificationType.EDU_RESOURCE_APPROVED),
                        any(),
                        any(),
                        eq("/education/resources"),
                        eq(false));
        // PHT đưa thẳng
        EduResourceItem own = item(6L, 9L);
        assertThat(service.share(vice, 6L, new ResourceShareRequest("SCHOOL", null))
                        .scope())
                .isEqualTo("SCHOOL");
        assertThat(i.getShareBy()).isEqualTo(8L);
        assertThat(own.getShareStatus()).isNull();
    }

    @Test
    void pendingItemCannotBeDeleted() {
        EduResourceItem i = item(5L, 7L);
        i.requestSchool(Instant.now());
        assertThatThrownBy(() -> service.delete(teacher, 5L))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_PENDING_REVIEW);
    }

    @Test
    void suggestionsRankSameGoalThenThemeAcrossYearsAndHideHidden() {
        EduResourceItem plain = item(1L, 7L);
        EduResourceItem theme = item(2L, 7L);
        EduResourceItem lastYearGoal = item(3L, 7L);
        EduResourceItem hidden = item(4L, 7L);
        hidden.changeHidden(true, Instant.now());
        when(items.findVisible(any(), any(), anyBoolean(), any()))
                .thenReturn(List.of(plain, theme, lastYearGoal, hidden));
        when(tags.findAllByItemIdIn(any()))
                .thenReturn(List.of(
                        // năm trước: MT 500 có cùng mã mục tiêu Bộ 77 với MT 20 năm nay
                        EduResourceItemTag.of(3L, "GOAL", 500L, 77L, null),
                        // năm trước: chủ đề khác id nhưng cùng tên
                        EduResourceItemTag.of(2L, "THEME", 300L, null, "Trường  mầm non của bé")));
        EduPlanGoal goal = mock(EduPlanGoal.class);
        when(goal.getId()).thenReturn(20L);
        when(goal.getCurriculumId()).thenReturn(77L);
        when(goals.findAllById(any())).thenReturn(List.of(goal));
        when(attachments.countByItemIds(any())).thenReturn(List.of());
        var page = service.list(teacher, query("ALL", List.of(20L), 30L, "trường mầm non của bé"));
        assertThat(page.items()).extracting(r -> r.id()).containsExactly(3L, 2L, 1L);
        assertThat(page.items()).extracting(r -> r.match()).containsExactly("GOAL", "THEME", null);
    }

    @Test
    void twoThousandItemsListedUnderOneSecond() {
        List<EduResourceItem> many = new ArrayList<>();
        LongStream.rangeClosed(1, 2000).forEach(id -> {
            EduResourceItem i = EduResourceItem.link(7L, "V" + id, 4L, "https://youtu.be/x", Instant.now());
            ReflectionTestUtils.setField(i, "id", id);
            many.add(i);
        });
        List<EduResourceItemTag> manyTags = new ArrayList<>();
        LongStream.rangeClosed(1, 2000)
                .forEach(id -> manyTags.add(EduResourceItemTag.of(id, "THEME", id, null, "Chủ đề " + id)));
        when(items.findVisible(any(), any(), anyBoolean(), any())).thenReturn(many);
        when(tags.findAllByItemIdIn(any())).thenReturn(manyTags);
        when(attachments.countByItemIds(any())).thenReturn(List.of());
        long start = System.nanoTime();
        var page = service.list(teacher, query("ALL", List.of(), 1500L, null));
        // SC-006
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000);
        assertThat(page.totalElements()).isEqualTo(2000);
        assertThat(page.items()).hasSize(50);
        assertThat(page.items().get(0).id()).isEqualTo(1500L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerReplacesTagsKeepingThemeAndDroppingGoals() {
        item(5L, 7L);
        when(goals.findAllById(any())).thenReturn(List.of());
        service.update(
                teacher,
                5L,
                new com.carenest.dto.eduplan.ResourceItemUpdateRequest(
                        null,
                        null,
                        null,
                        null,
                        new com.carenest.dto.eduplan.ResourceTagsRequest(30L, "Bản thân", List.of())));
        verify(tags).deleteAllByItemId(5L);
        var saved = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(tags).saveAll(saved.capture());
        assertThat((List<EduResourceItemTag>) saved.getValue()).singleElement().satisfies(t -> {
            assertThat(t.getKind()).isEqualTo("THEME");
            assertThat(t.getName()).isEqualTo("Bản thân");
        });
    }

    @Test
    void sameLinkPastedAgainReusesItem() {
        EduResourceItem existing = item(5L, 7L);
        when(items.findFirstByOwnerIdAndKindAndUrlOrderByIdAsc(
                        7L, "LINK", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                .thenReturn(Optional.of(existing));
        // EDU-83: dạng youtu.be của cùng video ⇒ dùng lại học liệu cũ
        assertThat(service.findOrCreateLink(teacher, "https://youtu.be/dQw4w9WgXcQ", null, 4L, null))
                .isSameAs(existing);
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    void returnedItemNotifiesOwnerWithReason() {
        EduResourceItem i = item(5L, 7L);
        i.requestSchool(Instant.now());
        service.review(leader, 5L, new ResourceReviewRequest(false, "Video chưa phù hợp lứa tuổi"));
        verify(notifications)
                .notifyUsers(
                        eq(List.of(7L)),
                        eq(NotificationType.EDU_RESOURCE_RETURNED),
                        any(),
                        contains("Video chưa phù hợp lứa tuổi"),
                        eq("/education/resources"),
                        eq(false));
    }
}
