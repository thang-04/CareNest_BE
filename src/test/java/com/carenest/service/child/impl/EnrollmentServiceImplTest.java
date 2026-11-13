package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.Enrollment;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.service.child.EnrollmentService.ChildClassStay;
import com.carenest.service.child.EnrollmentService.ClassStay;
import com.carenest.utils.enums.Gender;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit test service bằng Mockito (không DB): lớp của trẻ theo ngày, theo kỳ (xếp lớp). Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);
    private static final long CLASS_ID = 10L;
    private static final long OTHER_CLASS_ID = 11L;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ChildRepository childRepository;

    private EnrollmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentServiceImpl(enrollmentRepository, childRepository);
    }

    @Nested
    @DisplayName("findActiveClassroomIds")
    class FindActiveClassroomIds {

        @Test
        @DisplayName("Trả lớp đang học tại ngày theo repository (mới nhất trước)")
        void returnsActiveClassrooms() {
            given(enrollmentRepository.findActiveClassroomIds(1L, TODAY)).willReturn(List.of(OTHER_CLASS_ID, CLASS_ID));

            assertThat(service.findActiveClassroomIds(1L, TODAY)).containsExactly(OTHER_CLASS_ID, CLASS_ID);
        }

        @Test
        @DisplayName("Trẻ chờ xếp lớp/đã nghỉ ⇒ không có lớp")
        void childWithoutClass() {
            given(enrollmentRepository.findActiveClassroomIds(1L, TODAY)).willReturn(List.of());

            assertThat(service.findActiveClassroomIds(1L, TODAY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findChildIdsLeftSchool")
    class FindChildIdsLeftSchool {

        @Test
        @DisplayName("CHD-04: không có trẻ ⇒ rỗng, không truy vấn")
        void emptyInputSkipsQuery() {
            assertThat(service.findChildIdsLeftSchool(List.of(), TODAY)).isEmpty();

            verifyNoInteractions(enrollmentRepository, childRepository);
        }

        @Test
        @DisplayName("CHD-04: trả tập trẻ đã rời trường (bỏ trùng)")
        void returnsDistinctLeftChildren() {
            List<Long> childIds = List.of(1L, 2L, 3L);
            given(enrollmentRepository.findChildIdsLeftSchool(childIds, TODAY)).willReturn(List.of(2L, 3L, 2L));

            assertThat(service.findChildIdsLeftSchool(childIds, TODAY)).containsExactlyInAnyOrder(2L, 3L);
        }
    }

    @Nested
    @DisplayName("findChildrenInClassroom")
    class FindChildrenInClassroom {

        @Test
        @DisplayName("Trẻ đang học lớp tại ngày: bỏ trùng, sắp theo họ tên rồi id")
        void distinctChildrenSortedByName() {
            given(enrollmentRepository.findActiveByClassroomIds(List.of(CLASS_ID), TODAY))
                    .willReturn(List.of(
                            enrollment(3L, CLASS_ID, FROM, null),
                            enrollment(1L, CLASS_ID, FROM, null),
                            enrollment(2L, CLASS_ID, FROM, null),
                            enrollment(1L, CLASS_ID, FROM.plusDays(1), null)));
            given(childRepository.findAllById(Set.of(1L, 2L, 3L)))
                    .willReturn(List.of(child(3L, "Bé An giả"), child(1L, "Bé Bình giả"), child(2L, "Bé An giả")));

            List<ClassChildResponse> children = service.findChildrenInClassroom(CLASS_ID, TODAY);

            assertThat(children)
                    .extracting(ClassChildResponse::id, ClassChildResponse::fullName)
                    .containsExactly(tuple(2L, "Bé An giả"), tuple(3L, "Bé An giả"), tuple(1L, "Bé Bình giả"));
            assertThat(children.getFirst().dateOfBirth()).isEqualTo(LocalDate.of(2022, 3, 15));
        }

        @Test
        @DisplayName("Lớp không có trẻ ⇒ rỗng")
        void emptyClassroom() {
            given(enrollmentRepository.findActiveByClassroomIds(List.of(CLASS_ID), TODAY))
                    .willReturn(List.of());
            given(childRepository.findAllById(Set.of())).willReturn(List.of());

            assertThat(service.findChildrenInClassroom(CLASS_ID, TODAY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findChildrenInClassroomDuring")
    class FindChildrenInClassroomDuring {

        @Test
        @DisplayName("Trẻ có lịch sử học lớp trong kỳ (kể cả đã chuyển đi): bỏ trùng, sắp theo họ tên")
        void childrenWithHistoryInPeriod() {
            given(enrollmentRepository.findDuring(CLASS_ID, FROM, TO))
                    .willReturn(List.of(
                            enrollment(1L, CLASS_ID, FROM, FROM.plusDays(5)),
                            enrollment(2L, CLASS_ID, FROM, null),
                            enrollment(1L, CLASS_ID, FROM.plusDays(10), null)));
            given(childRepository.findAllById(Set.of(1L, 2L)))
                    .willReturn(List.of(child(1L, "Bé Cúc giả"), child(2L, "Bé Bình giả")));

            assertThat(service.findChildrenInClassroomDuring(CLASS_ID, FROM, TO))
                    .extracting(ClassChildResponse::id)
                    .containsExactly(2L, 1L);
        }
    }

    @Nested
    @DisplayName("findStaysInClassroom")
    class FindStaysInClassroom {

        @Test
        @DisplayName("Cắt đoạn học trong kỳ: vào lớp trước kỳ ⇒ from, chưa kết thúc/sau kỳ ⇒ to; sắp theo trẻ rồi ngày")
        void clampsStaysToPeriod() {
            given(enrollmentRepository.findDuring(CLASS_ID, FROM, TO))
                    .willReturn(List.of(
                            enrollment(2L, CLASS_ID, LocalDate.of(2026, 9, 1), null),
                            enrollment(1L, CLASS_ID, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 31)),
                            enrollment(1L, CLASS_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 10))));

            List<ClassStay> stays = service.findStaysInClassroom(CLASS_ID, FROM, TO);

            assertThat(stays)
                    .containsExactly(
                            new ClassStay(1L, FROM, LocalDate.of(2026, 11, 10)),
                            new ClassStay(1L, LocalDate.of(2026, 11, 20), TO),
                            new ClassStay(2L, FROM, TO));
            assertThat(stays.getFirst().covers(LocalDate.of(2026, 11, 15))).isFalse();
            assertThat(stays.get(1).covers(LocalDate.of(2026, 11, 20))).isTrue();
        }
    }

    @Nested
    @DisplayName("findChildStays")
    class FindChildStays {

        @Test
        @DisplayName("Các lớp trẻ đã học trong kỳ (chuyển lớp giữa kỳ), cắt trong kỳ, giữ thứ tự ngày vào lớp")
        void clampsChildStays() {
            given(enrollmentRepository.findChildDuring(1L, FROM, TO))
                    .willReturn(List.of(
                            enrollment(1L, CLASS_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 15)),
                            enrollment(1L, OTHER_CLASS_ID, LocalDate.of(2026, 11, 16), null)));

            assertThat(service.findChildStays(1L, FROM, TO))
                    .containsExactly(
                            new ChildClassStay(CLASS_ID, FROM, LocalDate.of(2026, 11, 15)),
                            new ChildClassStay(OTHER_CLASS_ID, LocalDate.of(2026, 11, 16), TO));
        }
    }

    @Nested
    @DisplayName("findActiveClassroomIdByChildIds")
    class FindActiveClassroomIdByChildIds {

        @Test
        @DisplayName("Không có trẻ ⇒ map rỗng, không truy vấn")
        void emptyInputSkipsQuery() {
            assertThat(service.findActiveClassroomIdByChildIds(List.of(), TODAY))
                    .isEmpty();

            verifyNoInteractions(enrollmentRepository);
        }

        @Test
        @DisplayName("Trẻ nhiều bản ghi đang mở ⇒ giữ bản ghi đầu (mới nhất); trẻ không có lớp không có khóa")
        void keepsLatestEnrollmentPerChild() {
            List<Long> childIds = List.of(1L, 2L, 3L);
            given(enrollmentRepository.findActiveByChildIdIn(childIds, TODAY))
                    .willReturn(List.of(
                            enrollment(1L, OTHER_CLASS_ID, LocalDate.of(2026, 11, 1), null),
                            enrollment(2L, CLASS_ID, LocalDate.of(2026, 9, 1), null),
                            enrollment(1L, CLASS_ID, LocalDate.of(2026, 9, 1), null)));

            assertThat(service.findActiveClassroomIdByChildIds(childIds, TODAY))
                    .containsOnlyKeys(1L, 2L)
                    .containsEntry(1L, OTHER_CLASS_ID)
                    .containsEntry(2L, CLASS_ID);
        }
    }

    private static Enrollment enrollment(long childId, long classroomId, LocalDate validFrom, LocalDate validTo) {
        Enrollment enrollment = Enrollment.start(childId, classroomId, validFrom);
        if (validTo != null) {
            enrollment.endOn(validTo);
        }
        return enrollment;
    }

    private static Child child(long id, String fullName) {
        Child child = Child.enroll("TR" + id, fullName, LocalDate.of(2022, 3, 15), Gender.FEMALE, true, null, FROM);
        ReflectionTestUtils.setField(child, "id", id);
        return child;
    }
}
