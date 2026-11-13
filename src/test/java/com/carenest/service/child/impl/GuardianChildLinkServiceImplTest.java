package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.GuardianRelationship;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test service bằng Mockito (không DB): liên kết tài khoản phụ huynh – trẻ. Dữ liệu giả. */
@ExtendWith(MockitoExtension.class)
class GuardianChildLinkServiceImplTest {

    private static final long PARENT_ID = 50L;
    private static final long CHILD_ID = 101L;
    private static final long SIBLING_ID = 102L;
    private static final Instant LINKED_AT = Instant.parse("2026-11-01T02:00:00Z");

    @Mock
    private ChildGuardianRepository childGuardianRepository;

    private GuardianChildLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GuardianChildLinkServiceImpl(childGuardianRepository);
    }

    @Nested
    @DisplayName("isGuardianOf")
    class IsGuardianOf {

        @Test
        @DisplayName("AUTH-04: tài khoản đã liên kết với trẻ ⇒ true")
        void linkedAccountIsGuardian() {
            given(childGuardianRepository.existsByUserIdAndChildId(PARENT_ID, CHILD_ID))
                    .willReturn(true);

            assertThat(service.isGuardianOf(PARENT_ID, CHILD_ID)).isTrue();
        }

        @Test
        @DisplayName("AUTH-04: chưa liên kết (hoặc đã gỡ) ⇒ false")
        void unlinkedAccountIsNotGuardian() {
            given(childGuardianRepository.existsByUserIdAndChildId(PARENT_ID, CHILD_ID))
                    .willReturn(false);

            assertThat(service.isGuardianOf(PARENT_ID, CHILD_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("findChildIdsOfGuardian")
    class FindChildIdsOfGuardian {

        @Test
        @DisplayName("Trả các trẻ đang liên kết với tài khoản, giữ thứ tự của repository")
        void returnsLinkedChildIds() {
            given(childGuardianRepository.findLinkedChildIds(PARENT_ID)).willReturn(List.of(CHILD_ID, SIBLING_ID));

            assertThat(service.findChildIdsOfGuardian(PARENT_ID)).containsExactly(CHILD_ID, SIBLING_ID);
        }

        @Test
        @DisplayName("Tài khoản chưa liên kết trẻ nào ⇒ danh sách rỗng")
        void noLinkedChildren() {
            given(childGuardianRepository.findLinkedChildIds(PARENT_ID)).willReturn(List.of());

            assertThat(service.findChildIdsOfGuardian(PARENT_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findLinksOfChild")
    class FindLinksOfChild {

        @Test
        @DisplayName("Map phụ huynh đã liên kết sang userId, childId, quan hệ, thời điểm liên kết")
        void mapsLinkedGuardians() {
            given(childGuardianRepository.findLinkedOfChild(CHILD_ID))
                    .willReturn(List.of(
                            linked(CHILD_ID, PARENT_ID, GuardianRelationship.MOTHER),
                            linked(CHILD_ID, 51L, GuardianRelationship.FATHER)));

            List<GuardianLinkResponse> links = service.findLinksOfChild(CHILD_ID);

            assertThat(links)
                    .containsExactly(
                            new GuardianLinkResponse(PARENT_ID, CHILD_ID, GuardianRelationship.MOTHER, LINKED_AT),
                            new GuardianLinkResponse(51L, CHILD_ID, GuardianRelationship.FATHER, LINKED_AT));
        }
    }

    @Nested
    @DisplayName("findLinksOfChildren")
    class FindLinksOfChildren {

        @Test
        @DisplayName("Không có trẻ ⇒ rỗng, không truy vấn")
        void emptyInputSkipsQuery() {
            assertThat(service.findLinksOfChildren(List.of())).isEmpty();

            verifyNoInteractions(childGuardianRepository);
        }

        @Test
        @DisplayName("Nhiều trẻ ⇒ một truy vấn, map đủ liên kết của từng trẻ")
        void mapsLinksOfManyChildren() {
            Set<Long> childIds = Set.of(CHILD_ID, SIBLING_ID);
            given(childGuardianRepository.findLinkedOfChildren(childIds))
                    .willReturn(List.of(
                            linked(CHILD_ID, PARENT_ID, GuardianRelationship.MOTHER),
                            linked(SIBLING_ID, PARENT_ID, GuardianRelationship.MOTHER)));

            assertThat(service.findLinksOfChildren(childIds))
                    .extracting(GuardianLinkResponse::childId)
                    .containsExactly(CHILD_ID, SIBLING_ID);
        }
    }

    @Nested
    @DisplayName("countGuardiansByChildIds")
    class CountGuardiansByChildIds {

        @Test
        @DisplayName("Không có trẻ ⇒ map rỗng, không truy vấn")
        void emptyInputSkipsQuery() {
            assertThat(service.countGuardiansByChildIds(List.of())).isEmpty();

            verifyNoInteractions(childGuardianRepository);
        }

        @Test
        @DisplayName("Đếm theo trẻ; trẻ chưa có phụ huynh liên kết không có khóa")
        void countsLinkedGuardiansPerChild() {
            List<Long> childIds = List.of(CHILD_ID, SIBLING_ID, 103L);
            given(childGuardianRepository.countLinkedByChildIds(childIds))
                    .willReturn(List.of(new Object[] {CHILD_ID, 2L}, new Object[] {SIBLING_ID, 1L}));

            assertThat(service.countGuardiansByChildIds(childIds))
                    .containsOnlyKeys(CHILD_ID, SIBLING_ID)
                    .containsEntry(CHILD_ID, 2L)
                    .containsEntry(SIBLING_ID, 1L);
        }
    }

    @Nested
    @DisplayName("deleteLink")
    class DeleteLink {

        @Test
        @DisplayName("AUTH-19: gỡ liên kết ⇒ xóa userId và thời điểm liên kết, giữ phụ huynh trên hồ sơ, flush ngay")
        void unlinksAccountButKeepsGuardianRecord() {
            ChildGuardian guardian = linked(CHILD_ID, PARENT_ID, GuardianRelationship.MOTHER);
            given(childGuardianRepository.findByUserIdAndChildId(PARENT_ID, CHILD_ID))
                    .willReturn(Optional.of(guardian));

            service.deleteLink(PARENT_ID, CHILD_ID);

            assertThat(guardian.getUserId()).isNull();
            assertThat(guardian.getLinkedAt()).isNull();
            assertThat(guardian.getFullName()).isEqualTo("Mẹ giả");
            assertThat(guardian.getPhone()).isEqualTo("0900000001");
            verify(childGuardianRepository).flush();
            verify(childGuardianRepository, never()).delete(guardian);
        }

        @Test
        @DisplayName("Không có liên kết tài khoản – trẻ ⇒ GUARDIAN_LINK_NOT_FOUND, không flush")
        void missingLinkIsRejected() {
            given(childGuardianRepository.findByUserIdAndChildId(PARENT_ID, CHILD_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteLink(PARENT_ID, CHILD_ID))
                    .isInstanceOf(GlobalException.class)
                    .extracting(ex -> ((GlobalException) ex).getApiCode())
                    .isEqualTo(ApiCode.GUARDIAN_LINK_NOT_FOUND);
            verify(childGuardianRepository, never()).flush();
        }
    }

    private static ChildGuardian linked(long childId, long userId, GuardianRelationship relationship) {
        ChildGuardian guardian =
                ChildGuardian.create(childId, 0, "Mẹ giả", relationship, "0900000001", "001200000001", null);
        guardian.linkAccount(userId, LINKED_AT);
        return guardian;
    }
}
