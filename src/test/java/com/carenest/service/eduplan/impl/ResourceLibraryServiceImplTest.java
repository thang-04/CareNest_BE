package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.ResourceItemUpdateRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduResourceAttachment;
import com.carenest.entity.eduplan.EduResourceItem;
import com.carenest.entity.eduplan.EduResourceItemTag;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.eduplan.*;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.ResourceLibraryService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduClassDocStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ResourceLibraryServiceImplTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private final EduResourceItemRepository items = mock(EduResourceItemRepository.class);
    private final EduResourceItemTagRepository tags = mock(EduResourceItemTagRepository.class);
    private final EduResourceAttachmentRepository attachments = mock(EduResourceAttachmentRepository.class);
    private final EduPlanGoalRepository goals = mock(EduPlanGoalRepository.class);
    private final EduClassDocRepository classDocRepository = mock(EduClassDocRepository.class);
    private final EduClassDocService classDocs = mock(EduClassDocService.class);
    private final ClassroomService classrooms = mock(ClassroomService.class);
    private final SchoolYearService schoolYears = mock(SchoolYearService.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final CurrentUser teacher = user(7L);
    private final CurrentUser other = user(8L);
    private EduClassDoc day;
    private ResourceLibraryServiceImpl service;

    private static CurrentUser user(long id) {
        return new CurrentUser(id, "kc-" + id, "u" + id, "U" + id, null, Set.of(CareNestRole.TEACHER));
    }

    @BeforeEach
    void setUp() {
        // Hôm nay 07/09/2026 theo giờ Việt Nam
        Clock clock = Clock.fixed(
                LocalDate.of(2026, 9, 7).atStartOfDay(VN).toInstant().plusSeconds(3600), VN);
        service = new ResourceLibraryServiceImpl(
                items,
                tags,
                attachments,
                goals,
                classDocRepository,
                classDocs,
                classrooms,
                schoolYears,
                storage,
                clock);
        when(items.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(attachments.countByItemIds(any())).thenReturn(List.of());
        when(goals.findAllById(any())).thenReturn(List.of());
        day = EduClassDoc.create("dd_1_2_2026-09-07", "day", 1L, 3L, 2L, LocalDate.of(2026, 9, 7), 7L);
        ReflectionTestUtils.setField(day, "id", 8L);
        when(classDocRepository.findAllById(any())).thenReturn(List.of(day));
        when(classrooms.findClassroomInfo(1L))
                .thenReturn(Optional.of(
                        new ClassroomInfoResponse(1L, "Mầm 1", 1L, 2L, null, null, null, 4L, List.of(), null, null)));
    }

    private EduResourceItem item(long id, long owner) {
        EduResourceItem i = EduResourceItem.link(owner, "Video " + id, 4L, "https://youtu.be/x" + id, Instant.now());
        ReflectionTestUtils.setField(i, "id", id);
        when(items.findById(id)).thenReturn(Optional.of(i));
        return i;
    }

    private void usedOn(long itemId, String date, EduClassDocStatus status) {
        EduResourceAttachment a =
                EduResourceAttachment.of(8L, date + "#hoc", "Hoạt động học", itemId, 0, 7L, Instant.now());
        ReflectionTestUtils.setField(a, "id", 30L);
        when(attachments.findAllByItemIdOrderByIdAsc(itemId)).thenReturn(List.of(a));
        when(classDocs.dayStatus(day)).thenReturn(status);
    }

    private static EduResourceItemTag tag(
            long id, long itemId, String kind, Long ref, Long curriculum, String name, Long year, Long attachment) {
        EduResourceItemTag t = EduResourceItemTag.of(itemId, kind, ref, curriculum, name, year, attachment);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private static ResourceLibraryService.Query query(List<Long> goalIds, Long themeId, String themeName) {
        return new ResourceLibraryService.Query(
                null, null, 4L, themeId, themeName, goalIds, null, null, null, null, 0, 50);
    }

    private static ResourceItemUpdateRequest rename(String title) {
        return new ResourceItemUpdateRequest(title, null, null, null, null, null);
    }

    @Test
    void createFileItemUsesGivenTitleElseFileNameWithoutExtension() {
        var stored = new FileStorage.StoredFile("hoclieu/a", "application/pdf", 8);
        // Tên người dùng đặt khi tải lên (cắt khoảng trắng); trống ⇒ tên tệp bỏ đuôi
        assertThat(service.createFileItem(teacher, 4L, stored, "bai-hat.pdf", "  Bài hát mùa thu  ", UUID.randomUUID())
                        .getTitle())
                .isEqualTo("Bài hát mùa thu");
        assertThat(service.createFileItem(teacher, 4L, stored, "bai-hat.pdf", "  ", UUID.randomUUID())
                        .getTitle())
                .isEqualTo("bai-hat");
        assertThat(service.createFileItem(teacher, 4L, stored, "bai-hat.pdf", null, UUID.randomUUID())
                        .getTitle())
                .isEqualTo("bai-hat");
    }

    @Test
    void otherTeacherCannotSeeEditOrDeleteMyItem() {
        item(5L, 7L);
        // EDU-84: kho chỉ cá nhân — người khác (kể cả cùng lớp) như không có học liệu này
        for (Runnable call : List.<Runnable>of(
                () -> service.delete(other, 5L),
                () -> service.contentUrl(other, 5L),
                () -> service.update(other, 5L, rename("X")),
                () -> service.usages(other, 5L)))
            assertThatThrownBy(call::run)
                    .isInstanceOf(GlobalException.class)
                    .extracting("apiCode")
                    .isEqualTo(ApiCode.RESOURCE_ITEM_NOT_FOUND);
        verify(items, never()).delete(any());
    }

    @Test
    void listReadsOnlyCallersLibrary() {
        when(items.findAllByOwnerId(7L)).thenReturn(List.of());
        service.list(teacher, query(List.of(), null, null));
        verify(items).findAllByOwnerId(7L);
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
    void deleteUsedOnlyInPastApprovedPlanRemovesFromAllPlans() {
        item(5L, 7L);
        usedOn(5L, "2026-09-04", EduClassDocStatus.APPROVED);
        // EDU-85: ngày dạy đã qua ⇒ xóa vĩnh viễn, gỡ khỏi mọi giáo án; không đổi trạng thái giáo án
        service.delete(teacher, 5L);
        verify(attachments).deleteAllByItemId(5L);
        verify(items).delete(any());
        verify(classDocs, never()).requireDayAccess(any(), any(), anyBoolean());
    }

    @Test
    void deleteLockedOnOrBeforeTeachingDayOfApprovedPlan() {
        item(5L, 7L);
        for (String date : List.of("2026-09-07", "2026-09-08")) {
            usedOn(5L, date, EduClassDocStatus.APPROVED);
            assertThatThrownBy(() -> service.delete(teacher, 5L))
                    .isInstanceOf(GlobalException.class)
                    .hasMessageContaining("Mầm 1")
                    .extracting("apiCode")
                    .isEqualTo(ApiCode.RESOURCE_ITEM_LOCKED);
        }
        var usages = service.usages(teacher, 5L);
        assertThat(usages.locked()).isTrue();
        assertThat(usages.deletableFrom()).isEqualTo(LocalDate.of(2026, 9, 9));
        verify(attachments, never()).deleteAllByItemId(any());
        verify(items, never()).delete(any());
    }

    @Test
    void reviewPlanLocksDeleteAndRenameButNotTags() {
        EduResourceItem i = item(5L, 7L);
        usedOn(5L, "2026-08-01", EduClassDocStatus.REVIEW);
        assertThatThrownBy(() -> service.delete(teacher, 5L))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_ITEM_LOCKED);
        // EDU-88: đổi tên chặn như xóa
        assertThatThrownBy(() -> service.update(teacher, 5L, rename("Tên mới")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_ITEM_LOCKED);
        assertThat(i.getTitle()).isEqualTo("Video 5");
        var usages = service.usages(teacher, 5L);
        assertThat(usages.deletableFrom()).isNull();
        assertThat(usages.rows()).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo("REVIEW");
            assertThat(r.locked()).isTrue();
        });
        // Sửa nhãn vẫn được (nhãn không hiện trong giáo án)
        service.update(
                teacher,
                5L,
                new ResourceItemUpdateRequest(
                        null,
                        null,
                        null,
                        List.of(new ResourceItemUpdateRequest.ThemeTag(null, "Gia đình", null)),
                        null,
                        null));
        verify(tags).saveAll(argThat(list -> list.iterator().hasNext()));
    }

    @Test
    void draftPlanDoesNotLock() {
        item(5L, 7L);
        usedOn(5L, "2026-09-10", EduClassDocStatus.DRAFT);
        service.update(teacher, 5L, rename("Tên mới"));
        assertThat(service.usages(teacher, 5L).locked()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void manualTagsAddedAndOnlyManualTagsRemovable() {
        item(5L, 7L);
        EduResourceItemTag auto = tag(1L, 5L, "THEME", 3L, null, "Trường mầm non", 2L, 30L);
        EduResourceItemTag manual = tag(2L, 5L, "THEME", null, null, "Gia đình", null, null);
        when(tags.findAllByItemIdIn(any())).thenReturn(List.of(auto, manual));
        service.update(
                teacher,
                5L,
                new ResourceItemUpdateRequest(
                        null,
                        null,
                        null,
                        List.of(
                                new ResourceItemUpdateRequest.ThemeTag(null, "gia  đình", null),
                                new ResourceItemUpdateRequest.ThemeTag(null, "Bản thân", 2L)),
                        null,
                        List.of(1L, 2L)));
        // EDU-87: nhãn tự thêm (1) không bỏ được; nhãn tay (2) bỏ được
        var removed = ArgumentCaptor.forClass(Iterable.class);
        verify(tags).deleteAll(removed.capture());
        assertThat((Iterable<EduResourceItemTag>) removed.getValue()).containsExactly(manual);
        var saved = ArgumentCaptor.forClass(List.class);
        verify(tags).saveAll(saved.capture());
        assertThat((List<EduResourceItemTag>) saved.getValue())
                .extracting(EduResourceItemTag::getName)
                .containsExactly("gia  đình", "Bản thân");
    }

    @Test
    void suggestionsRankSameGoalThenThemeAcrossYears() {
        EduResourceItem plain = item(1L, 7L);
        EduResourceItem theme = item(2L, 7L);
        EduResourceItem lastYearGoal = item(3L, 7L);
        when(items.findAllByOwnerId(7L)).thenReturn(List.of(plain, theme, lastYearGoal));
        when(tags.findAllByItemIdIn(any()))
                .thenReturn(List.of(
                        // năm trước: MT 500 có cùng mã mục tiêu Bộ 77 với MT 20 năm nay
                        tag(1L, 3L, "GOAL", 500L, 77L, null, null, 40L),
                        // năm trước: chủ đề khác id nhưng cùng tên
                        tag(2L, 2L, "THEME", 300L, null, "Trường  mầm non của bé", 1L, 41L)));
        when(schoolYears.getSchoolYears(teacher, null))
                .thenReturn(List.of(new SchoolYearDetailResponse(
                        1L,
                        "2025-2026",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false)));
        EduPlanGoal goal = mock(EduPlanGoal.class);
        when(goal.getId()).thenReturn(20L);
        when(goal.getCurriculumId()).thenReturn(77L);
        when(goals.findAllById(any())).thenReturn(List.of(goal));
        var page = service.list(teacher, query(List.of(20L), 30L, "trường mầm non của bé"));
        assertThat(page.items()).extracting(r -> r.id()).containsExactly(3L, 2L, 1L);
        assertThat(page.items()).extracting(r -> r.match()).containsExactly("GOAL", "THEME", null);
        assertThat(page.items().get(1).taughtThemes()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("Trường  mầm non của bé");
            assertThat(t.schoolYearName()).isEqualTo("2025-2026");
        });
    }

    @Test
    void listFiltersByTaughtThemeAndYear() {
        EduResourceItem a = item(1L, 7L);
        EduResourceItem b = item(2L, 7L);
        when(items.findAllByOwnerId(7L)).thenReturn(List.of(a, b));
        when(tags.findAllByItemIdIn(any()))
                .thenReturn(List.of(
                        tag(1L, 1L, "THEME", 3L, null, "Gia đình", 1L, 40L),
                        tag(2L, 2L, "THEME", 4L, null, "Gia đình", 2L, 41L)));
        var page = service.list(
                teacher,
                new ResourceLibraryService.Query(
                        null, null, null, null, null, List.of(), null, null, "gia đình", 2L, 0, 50));
        assertThat(page.items()).extracting(r -> r.id()).containsExactly(2L);
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
                .forEach(id -> manyTags.add(tag(id, id, "THEME", id, null, "Chủ đề " + id, null, id)));
        when(items.findAllByOwnerId(7L)).thenReturn(many);
        when(tags.findAllByItemIdIn(any())).thenReturn(manyTags);
        long start = System.nanoTime();
        var page = service.list(teacher, query(List.of(), 1500L, null));
        // SC-006
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000);
        assertThat(page.totalElements()).isEqualTo(2000);
        assertThat(page.items()).hasSize(50);
        assertThat(page.items().get(0).id()).isEqualTo(1500L);
    }

    @Test
    void sameLinkPastedAgainReusesItem() {
        EduResourceItem existing = item(5L, 7L);
        when(items.findFirstByOwnerIdAndKindAndUrlOrderByIdAsc(
                        7L, "LINK", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                .thenReturn(Optional.of(existing));
        // EDU-83: dạng youtu.be của cùng video ⇒ dùng lại học liệu cũ
        assertThat(service.findOrCreateLink(teacher, "https://youtu.be/dQw4w9WgXcQ", null, 4L))
                .isSameAs(existing);
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void attachmentTagsCarryAttachmentAndSchoolYear() {
        EduPlanGoal goal = mock(EduPlanGoal.class);
        when(goal.getId()).thenReturn(20L);
        when(goal.getCurriculumId()).thenReturn(77L);
        when(goals.findAllById(any())).thenReturn(List.of(goal));
        service.tagAttachment(
                5L, 40L, 2L, new com.carenest.dto.eduplan.ResourceTagsRequest(3L, " Gia đình ", List.of(20L)));
        var saved = ArgumentCaptor.forClass(List.class);
        verify(tags).saveAll(saved.capture());
        assertThat((List<EduResourceItemTag>) saved.getValue())
                .satisfiesExactly(
                        t -> {
                            assertThat(t.getKind()).isEqualTo("THEME");
                            assertThat(t.getName()).isEqualTo("Gia đình");
                            assertThat(t.getSchoolYearId()).isEqualTo(2L);
                            assertThat(t.getAttachmentId()).isEqualTo(40L);
                        },
                        t -> {
                            assertThat(t.getKind()).isEqualTo("GOAL");
                            assertThat(t.getCurriculumGoalId()).isEqualTo(77L);
                            assertThat(t.getAttachmentId()).isEqualTo(40L);
                        });
    }
}
