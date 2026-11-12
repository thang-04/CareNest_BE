package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.common.ImageContent;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.dto.meals.InspectionReopenRequest;
import com.carenest.dto.meals.InspectionSaveRequest;
import com.carenest.dto.meals.InspectionSheetResponse;
import com.carenest.dto.meals.InspectionSignRequest;
import com.carenest.entity.meals.InspectionSheet;
import com.carenest.exception.GlobalException;
import com.carenest.utils.enums.FileCategory;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.account.UserSignatureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// Khung dòng được hỏi theo từng mẫu với tham số khác nhau, nên không bắt buộc mọi stub phải khớp chặt
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-10T01:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private static final Long CAMPUS = 1L;
    private static final String IMAGE_KEY = "22222222-2222-2222-2222-222222222222";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final CurrentUser PHT = TestPermissions.userWithDefaults(7L, CareNestRole.VICE_PRINCIPAL);

    @Mock
    private InspectionSheetRepository sheetRepository;

    @Mock
    private FoodSampleRepository campusLookup;

    @Mock
    private InspectionSkeleton skeleton;

    @Mock
    private CampusAccessPolicy campusAccessPolicy;

    @Mock
    private UserSignatureService userSignatureService;

    @Mock
    private FileStorage imageStorage;

    private final AtomicReference<InspectionSheet> stored = new AtomicReference<>();
    private InspectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InspectionServiceImpl(
                sheetRepository,
                campusLookup,
                skeleton,
                campusAccessPolicy,
                userSignatureService,
                imageStorage,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(campusLookup.campusExists(CAMPUS)).thenReturn(true);
        lenient()
                .when(campusAccessPolicy.canEditCampus(PHT, Permission.FOOD_SAFETY_APPROVE, CAMPUS))
                .thenReturn(true);
        // Kho giả: một biên bản (điểm trường, ngày, mẫu) giữ trong bộ nhớ
        lenient()
                .when(sheetRepository.findByCampusIdAndSheetDateAndTab(eq(CAMPUS), eq(DAY), any()))
                .thenAnswer(call -> Optional.ofNullable(stored.get()));
        lenient()
                .when(sheetRepository.findByCampusIdAndSheetDate(CAMPUS, DAY))
                .thenAnswer(call -> stored.get() == null ? List.of() : List.of(stored.get()));
        lenient().when(sheetRepository.saveAndFlush(any(InspectionSheet.class))).thenAnswer(call -> {
            InspectionSheet sheet = call.getArgument(0);
            stored.set(sheet);
            return sheet;
        });
    }

    private static Map<String, Object> row(String key, String sensory) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("dishName", "Cá hấp");
        row.put("servings", 40);
        if (sensory != null) {
            row.put("sensory", sensory);
        }
        return row;
    }

    private static Map<String, Object> freshRow(String key, String sensory) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("name", "Thịt");
        row.put("sensory", sensory);
        return row;
    }

    @SafeVarargs
    private static InspectionSaveRequest save(int expectedVersion, Map<String, Object>... rows) {
        return new InspectionSaveRequest(expectedVersion, Map.of("place", "Bếp ăn"), List.of(rows));
    }

    private static void assertApiCode(Throwable thrown, ApiCode expected) {
        assertThat(thrown).isInstanceOf(GlobalException.class);
        assertThat(((GlobalException) thrown).getApiCode()).isEqualTo(expected);
    }

    private void savedAndSignable() {
        service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, row("LUNCH:KINDERGARTEN:5", "PASS")));
        when(userSignatureService.findForSigning(PHT, null))
                .thenReturn(Optional.of(new ImageContent(PNG, "image/png")));
        when(imageStorage.store(FileCategory.INSPECTION_SIGNATURE, CAMPUS, PNG))
                .thenReturn(new FileStorage.StoredFile(IMAGE_KEY, "image/png", PNG.length));
    }

    @Test
    @DisplayName("AC-1: chưa lưu thì trả khung dựng từ dữ liệu nguồn cho đủ năm mẫu")
    void dayShowsSkeletonWhenNothingSaved() {
        when(skeleton.rows(InspectionTab.PROCESS, CAMPUS, DAY)).thenReturn(List.of(row("LUNCH:KINDERGARTEN:5", null)));

        InspectionDayResponse day = service.getDay(PHT, CAMPUS, DAY);

        assertThat(day.canEdit()).isTrue();
        assertThat(day.sheets()).hasSize(InspectionTab.values().length);
        InspectionSheetResponse process = day.sheets().stream()
                .filter(s -> s.tab() == InspectionTab.PROCESS)
                .findFirst()
                .orElseThrow();
        assertThat(process.saved()).isFalse();
        assertThat(process.rows()).hasSize(1);
    }

    @Test
    @DisplayName("AC-2: lưu nháp rồi đọc lại giữ ô PHT nhập và lấy lại ô hệ thống từ nguồn mới")
    void saveKeepsInputAndRefreshesSystemCells() {
        Map<String, Object> input = row("LUNCH:KINDERGARTEN:5", "PASS");
        input.put("prepDoneAt", "08:30");
        InspectionSheetResponse saved = service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, input));
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.status()).isEqualTo(InspectionStatus.DRAFT);

        // Nguồn đổi suất từ 40 sang 42 sau khi lưu nháp
        Map<String, Object> source = row("LUNCH:KINDERGARTEN:5", null);
        source.put("servings", 42);
        when(skeleton.rows(InspectionTab.PROCESS, CAMPUS, DAY)).thenReturn(List.of(source));
        InspectionDayResponse day = service.getDay(PHT, CAMPUS, DAY);

        Map<String, Object> shown = day.sheets().stream()
                .filter(s -> s.tab() == InspectionTab.PROCESS)
                .findFirst()
                .orElseThrow()
                .rows()
                .getFirst();
        assertThat(shown)
                .containsEntry("servings", 42)
                .containsEntry("prepDoneAt", "08:30")
                .containsEntry("sensory", "PASS");
    }

    @Test
    @DisplayName("Lưu lệch phiên bản ⇒ xung đột, không ghi đè bản người khác vừa lưu")
    void staleVersionIsRejected() {
        service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, row("a", null)));

        assertThatThrownBy(() -> service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, row("a", null))))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_STALE));
    }

    @Test
    @DisplayName("Ô lạ hoặc sai kiểu bị từ chối")
    void invalidCellsAreRejected() {
        Map<String, Object> unknown = row("a", null);
        unknown.put("hack", "x");
        Map<String, Object> badTime = row("b", null);
        badTime.put("prepDoneAt", "25:99");
        Map<String, Object> badValue = row("c", "MAYBE");

        for (Map<String, Object> bad : List.of(unknown, badTime, badValue)) {
            assertThatThrownBy(() -> service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, bad)))
                    .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_ROW_INVALID));
        }
        verify(sheetRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("INS-01: không có FOOD_SAFETY_APPROVE ở điểm trường thì không ghi được; ngày chưa tới bị từ chối")
    void writeNeedsPermissionAndNotFutureDate() {
        CurrentUser other = TestPermissions.userWithDefaults(8L, CareNestRole.TEACHER);

        assertThatThrownBy(() -> service.save(other, CAMPUS, DAY, InspectionTab.PROCESS, save(0, row("a", null))))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_ACCESS_DENIED));
        assertThatThrownBy(() ->
                        service.save(PHT, CAMPUS, DAY.plusDays(1), InspectionTab.PROCESS, save(0, row("a", null))))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_DATE_FUTURE));
    }

    @Test
    @DisplayName("AC-3: ký khi còn dòng chưa Đạt / Không đạt ⇒ 422, biên bản vẫn nháp")
    void signNeedsEverySensoryChosen() {
        service.save(
                PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, row("a", "PASS"), row("b", null), row("c", "FAIL")));

        assertThatThrownBy(() -> service.sign(PHT, CAMPUS, DAY, InspectionTab.PROCESS, null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_SENSORY_MISSING));
        assertThat(stored.get().getStatus()).isEqualTo(InspectionStatus.DRAFT);
    }

    @Test
    @DisplayName(
            "INS-02, QĐ 1246: dòng Không đạt (cảm quan hoặc xét nghiệm nhanh) phải ghi biện pháp xử lý mới ký được")
    void signNeedsActionForFailedRows() {
        // M1 tươi không có cột tên món/số suất của M2, M3
        Map<String, Object> failedSensory = freshRow("a", "FAIL");
        Map<String, Object> failedRapid = freshRow("b", "PASS");
        failedRapid.put("rapidTest", "FAIL");
        Map<String, Object> blankNote = freshRow("c", "FAIL");
        blankNote.put("note", "   ");
        service.save(PHT, CAMPUS, DAY, InspectionTab.FRESH, save(0, failedSensory, failedRapid, blankNote));

        assertThatThrownBy(() -> service.sign(PHT, CAMPUS, DAY, InspectionTab.FRESH, null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_ACTION_MISSING))
                .hasMessageContaining("3");

        // Ghi đủ biện pháp xử lý cho cả ba dòng ⇒ ký được
        for (Map<String, Object> row : List.of(failedSensory, failedRapid, blankNote)) {
            row.put("note", "Trả lại nhà cung cấp");
        }
        service.save(PHT, CAMPUS, DAY, InspectionTab.FRESH, save(1, failedSensory, failedRapid, blankNote));
        when(userSignatureService.findForSigning(PHT, null))
                .thenReturn(Optional.of(new ImageContent(PNG, "image/png")));
        when(imageStorage.store(FileCategory.INSPECTION_SIGNATURE, CAMPUS, PNG))
                .thenReturn(new FileStorage.StoredFile(IMAGE_KEY, "image/png", PNG.length));

        assertThat(service.sign(PHT, CAMPUS, DAY, InspectionTab.FRESH, null).status())
                .isEqualTo(InspectionStatus.SIGNED);
    }

    @Test
    @DisplayName("INS-02: chưa lưu hoặc chưa có chữ ký ảnh trong tài khoản thì không ký được")
    void signNeedsSavedSheetAndSignatureImage() {
        assertThatThrownBy(() -> service.sign(PHT, CAMPUS, DAY, InspectionTab.PROCESS, null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_NOT_SAVED));

        service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, row("a", "PASS")));
        when(userSignatureService.findForSigning(PHT, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sign(PHT, CAMPUS, DAY, InspectionTab.PROCESS, null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_SIGNATURE_REQUIRED));
    }

    @Test
    @DisplayName("M5 không bắt buộc cột Đạt / Không đạt khi ký")
    void sampleSheetSignsWithoutSensory() {
        service.save(PHT, CAMPUS, DAY, InspectionTab.SAMPLE, save(0, row("sample:1", null)));
        when(userSignatureService.findForSigning(PHT, null))
                .thenReturn(Optional.of(new ImageContent(PNG, "image/png")));
        when(imageStorage.store(FileCategory.INSPECTION_SIGNATURE, CAMPUS, PNG))
                .thenReturn(new FileStorage.StoredFile(IMAGE_KEY, "image/png", PNG.length));

        InspectionSheetResponse signed = service.sign(PHT, CAMPUS, DAY, InspectionTab.SAMPLE, null);

        assertThat(signed.status()).isEqualTo(InspectionStatus.SIGNED);
    }

    @Test
    @DisplayName("AC-4, AC-7: ký chép ảnh sang kho riêng, khóa biên bản, bản chụp không đổi khi nguồn đổi")
    void signLocksAndSnapshots() {
        savedAndSignable();

        InspectionSheetResponse signed =
                service.sign(PHT, CAMPUS, DAY, InspectionTab.PROCESS, new InspectionSignRequest(null));

        assertThat(signed.status()).isEqualTo(InspectionStatus.SIGNED);
        assertThat(signed.signature().imageKey()).isEqualTo(IMAGE_KEY);
        assertThat(signed.signature().documentVersion()).isEqualTo(1);
        assertThat(signed.history())
                .extracting(InspectionSheetResponse.Event::action)
                .containsExactly("SIGNED");
        verify(imageStorage).store(FileCategory.INSPECTION_SIGNATURE, CAMPUS, PNG);

        // Nguồn đổi sau khi ký: biên bản vẫn trả bản chụp lúc ký
        Map<String, Object> changed = row("LUNCH:KINDERGARTEN:5", null);
        changed.put("servings", 99);
        lenient().when(skeleton.rows(InspectionTab.PROCESS, CAMPUS, DAY)).thenReturn(List.of(changed));
        InspectionSheetResponse again = service.getDay(PHT, CAMPUS, DAY).sheets().stream()
                .filter(s -> s.tab() == InspectionTab.PROCESS)
                .findFirst()
                .orElseThrow();
        assertThat(again.rows().getFirst()).containsEntry("servings", 40);

        // Sửa trực tiếp sau khi ký ⇒ 409
        assertThatThrownBy(() -> service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(1, row("x", "PASS"))))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_SIGNED));
    }

    @Test
    @DisplayName("AC-5, INS-04: sửa lại cần lý do; có lý do ⇒ về nháp, chữ ký cũ vào lịch sử, ký lại được")
    void reopenNeedsReasonAndKeepsHistory() {
        savedAndSignable();
        service.sign(PHT, CAMPUS, DAY, InspectionTab.PROCESS, null);

        assertThatThrownBy(() ->
                        service.reopen(PHT, CAMPUS, DAY, InspectionTab.PROCESS, new InspectionReopenRequest("ab")))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_REASON_REQUIRED));

        InspectionSheetResponse reopened = service.reopen(
                PHT, CAMPUS, DAY, InspectionTab.PROCESS, new InspectionReopenRequest("  Nhập sai giờ  "));

        assertThat(reopened.status()).isEqualTo(InspectionStatus.DRAFT);
        assertThat(reopened.signature()).isNull();
        assertThat(reopened.version()).isEqualTo(2);
        assertThat(reopened.history())
                .extracting(InspectionSheetResponse.Event::action)
                .containsExactly("SIGNED", "REOPENED");
        assertThat(reopened.history().getLast().note()).isEqualTo("Nhập sai giờ");
        assertThat(reopened.history().getLast().imageKey()).isEqualTo(IMAGE_KEY);

        // Lưu và ký lại sau khi sửa
        Map<String, Object> failed = row("LUNCH:KINDERGARTEN:5", "FAIL");
        failed.put("note", "Loại bỏ, nấu lại");
        service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(2, failed));
        assertThat(service.sign(PHT, CAMPUS, DAY, InspectionTab.PROCESS, null).history())
                .hasSize(3);
    }

    @Test
    @DisplayName("Sửa lại biên bản chưa ký ⇒ 409")
    void reopenUnsignedIsRejected() {
        service.save(PHT, CAMPUS, DAY, InspectionTab.PROCESS, save(0, row("a", "PASS")));

        assertThatThrownBy(() ->
                        service.reopen(PHT, CAMPUS, DAY, InspectionTab.PROCESS, new InspectionReopenRequest("lý do")))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_NOT_SIGNED));
    }

    @Test
    @DisplayName("AC-8: chỉ tải được ảnh nằm trên biên bản của điểm trường đó")
    void imageMustBelongToCampusSheet() {
        when(sheetRepository.existsWithImage(eq(CAMPUS), eq(IMAGE_KEY), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getSignatureImage(PHT, CAMPUS, IMAGE_KEY))
                .satisfies(ex -> assertApiCode(ex, ApiCode.SIGNATURE_NOT_FOUND));
        assertThatThrownBy(() -> service.getSignatureImage(PHT, CAMPUS, "../x"))
                .satisfies(ex -> assertApiCode(ex, ApiCode.SIGNATURE_NOT_FOUND));
        verify(imageStorage, never()).loadBytes(any(), any());

        when(sheetRepository.existsWithImage(eq(CAMPUS), eq(IMAGE_KEY), any())).thenReturn(true);
        when(imageStorage.loadBytes(FileCategory.INSPECTION_SIGNATURE, IMAGE_KEY)).thenReturn(PNG);
        assertThat(service.getSignatureImage(PHT, CAMPUS, IMAGE_KEY).contentType())
                .isEqualTo("image/png");
    }
}
