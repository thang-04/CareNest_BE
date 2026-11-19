package com.carenest.service.meals.impl;

import com.carenest.dto.meals.DryIntakeRequest;
import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.dto.meals.StockReceiptResponse;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.StockLot;
import com.carenest.entity.meals.StockMovement;
import com.carenest.entity.meals.StockReceipt;
import com.carenest.entity.meals.StockReceiptLine;
import com.carenest.entity.meals.StockRejectedLine;
import com.carenest.entity.meals.Supplier;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.StockMapper;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.StockLotRepository;
import com.carenest.repository.meals.StockMovementRepository;
import com.carenest.repository.meals.StockReceiptRepository;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StockMovementType;
import com.carenest.utils.enums.StockReceiptStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockReceiptServiceImpl implements StockReceiptService {
    private static final String LOG_PREFIX = "[StockReceiptServiceImpl]";

    private final StockReceiptRepository receiptRepository;
    private final StockLotRepository lotRepository;
    private final StockMovementRepository movementRepository;
    private final FoodRepository foodRepository;
    private final SupplierRepository supplierRepository;
    private final StockLedgerService stockLedgerService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final StockMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<StockReceiptResponse> getReceipts(
            CurrentUser currentUser, Long campusId, LocalDate from, LocalDate to, StockReceiptStatus status) {
        String logPrefix = LOG_PREFIX + "|getReceipts|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            List<StockReceipt> receipts =
                    receiptRepository.findByCampusIdOrderByReceivedDateDescIdDesc(campusId).stream()
                            .filter(r -> from == null || !r.getReceivedDate().isBefore(from))
                            .filter(r -> to == null || !r.getReceivedDate().isAfter(to))
                            .filter(r -> status == null || r.getStatus() == status)
                            .toList();
            List<StockReceiptResponse> response = toResponses(currentUser, receipts);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StockReceiptResponse getReceipt(CurrentUser currentUser, Long receiptId) {
        String logPrefix = LOG_PREFIX + "|getReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        try {
            StockReceipt receipt = findReceipt(receiptId);
            campusAccessPolicy.checkCanReadCampus(currentUser, receipt.getCampusId());
            StockReceiptResponse response = toResponse(currentUser, receipt);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public StockReceiptResponse createReceipt(CurrentUser currentUser, StockReceiptRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createReceipt|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser, request.campusId());
            StockReceipt receipt = StockReceipt.draft(request.campusId(), currentUser.userId());
            apply(receipt, request, currentUser.userId());
            receipt = receiptRepository.save(receipt);
            log.info("{}|receiptId={}|END", logPrefix, receipt.getId());
            return toResponse(currentUser, receipt);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public StockReceiptResponse updateReceipt(CurrentUser currentUser, Long receiptId, StockReceiptRequest request) {
        String logPrefix = LOG_PREFIX + "|updateReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        try {
            StockReceipt receipt = findDraftForManage(currentUser, receiptId);
            // Phiếu thuộc một điểm trường; đổi điểm trường = lập phiếu mới
            if (!receipt.getCampusId().equals(request.campusId())) {
                throw GlobalException.of(ApiCode.STOCK_RECEIPT_STATUS_INVALID);
            }
            apply(receipt, request, currentUser.userId());
            receiptRepository.flush();
            StockReceiptResponse response = toResponse(currentUser, receipt);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteReceipt(CurrentUser currentUser, Long receiptId) {
        String logPrefix = LOG_PREFIX + "|deleteReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        try {
            receiptRepository.delete(findDraftForManage(currentUser, receiptId));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public StockReceiptResponse confirmReceipt(CurrentUser currentUser, Long receiptId) {
        String logPrefix = LOG_PREFIX + "|confirmReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        try {
            StockReceipt receipt = findReceipt(receiptId);
            checkCanManage(currentUser, receipt.getCampusId());
            lotRepository.lockCampus(receipt.getCampusId());
            if (!receipt.isDraft()) {
                throw GlobalException.of(ApiCode.STOCK_RECEIPT_STATUS_INVALID);
            }
            confirmInto(receipt, currentUser.userId());
            StockReceiptResponse response = toResponse(currentUser, receipt);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public StockReceiptResponse cancelReceipt(CurrentUser currentUser, Long receiptId, String reason) {
        String logPrefix = LOG_PREFIX + "|cancelReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        try {
            StockReceipt receipt = findReceipt(receiptId);
            checkCanManage(currentUser, receipt.getCampusId());
            lotRepository.lockCampus(receipt.getCampusId());
            if (receipt.getStatus() != StockReceiptStatus.CONFIRMED) {
                throw GlobalException.of(ApiCode.STOCK_RECEIPT_STATUS_INVALID);
            }
            List<Long> lineIds =
                    receipt.getLines().stream().map(StockReceiptLine::getId).toList();
            List<StockLot> lots = lotRepository.findAllById(lineIds);
            Map<Long, BigDecimal> balances =
                    movementRepository
                            .sumByLotIds(lots.stream().map(StockLot::getId).toList())
                            .stream()
                            .collect(Collectors.toMap(
                                    StockMovementRepository.LotBalance::getLotId,
                                    StockMovementRepository.LotBalance::getBalance));
            // Không sửa bút toán (NUT-11): chỉ đảo được khi lô còn nguyên, chưa bị trừ
            for (StockLot lot : lots) {
                BigDecimal balance = balances.getOrDefault(lot.getId(), BigDecimal.ZERO);
                if (balance.compareTo(lot.getQuantity()) != 0) {
                    throw GlobalException.of(ApiCode.STOCK_RECEIPT_LOT_USED, lot.getLotNo());
                }
            }
            String trimmed = reason.trim();
            for (StockLot lot : lots) {
                movementRepository.save(StockMovement.of(
                        lot,
                        StockMovementType.RECEIPT_CANCEL,
                        lot.getQuantity().negate(),
                        null,
                        receipt.getId(),
                        trimmed,
                        currentUser.userId()));
            }
            receipt.cancel(currentUser.userId(), clock.instant(), trimmed);
            receiptRepository.flush();
            StockReceiptResponse response = toResponse(currentUser, receipt);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public StockReceiptResponse recordIntake(
            CurrentUser currentUser, Long campusId, LocalDate date, Long forecastId, DryIntakeRequest request) {
        String logPrefix = LOG_PREFIX + "|recordIntake|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|forecastId=" + forecastId;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser, campusId);
            if (!supplierRepository.existsById(request.supplierId())) {
                throw GlobalException.of(ApiCode.SUPPLIER_NOT_FOUND);
            }
            Map<Long, Food> foods = foodRepository
                    .findAllById(request.lines().stream()
                            .map(DryIntakeRequest.DryIntakeLineInput::foodId)
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(Food::getId, Function.identity()));
            List<StockReceiptLine> passed = new ArrayList<>();
            List<StockRejectedLine> rejected = new ArrayList<>();
            for (DryIntakeRequest.DryIntakeLineInput input : request.lines()) {
                Food food = foods.get(input.foodId());
                StockReceiptLine line = toLine(
                        new StockReceiptRequest.ReceiptLineInput(
                                input.foodId(), input.lotNo(), input.expiryDate(), input.quantity(), input.unitPrice()),
                        food,
                        date);
                String note = input.note() == null ? "" : input.note().trim();
                if (Boolean.TRUE.equals(input.passed())) {
                    passed.add(line);
                } else if (note.isEmpty()) {
                    // INS-02, QĐ 1246: hàng không đạt phải ghi biện pháp xử lý
                    throw GlobalException.of(ApiCode.DRY_INTAKE_NOTE_REQUIRED, food.getName());
                } else {
                    rejected.add(new StockRejectedLine(
                            line.getFoodId(), line.getLotNo(), line.getExpiryDate(), line.getQuantity(), note));
                }
            }
            lotRepository.lockCampus(campusId);
            StockReceipt receipt = StockReceipt.intake(campusId, forecastId, rejected, currentUser.userId());
            String note = request.note() == null ? "" : request.note().trim();
            receipt.changeDraft(request.supplierId(), date, note, passed, currentUser.userId());
            receipt = receiptRepository.save(receipt);
            confirmInto(receipt, currentUser.userId());
            StockReceiptResponse response = toResponse(currentUser, receipt);
            log.info("{}|receiptId={}|END", logPrefix, receipt.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockReceiptResponse> getIntakeReceipts(CurrentUser currentUser, Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getIntakeReceipts|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            List<StockReceiptResponse> response = toResponses(
                    currentUser,
                    receiptRepository.findByCampusIdAndReceivedDateAndFromIntakeTrueOrderByIdAsc(campusId, date));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> receivedByFood(Long forecastId) {
        String logPrefix = LOG_PREFIX + "|receivedByFood|forecastId=" + forecastId;
        log.info("{}|START", logPrefix);
        try {
            Map<Long, BigDecimal> received = new HashMap<>();
            receiptRepository
                    .sumLinesByForecast(forecastId, StockReceiptStatus.CONFIRMED)
                    .forEach(row -> received.put(row.getFoodId(), row.getQuantity()));
            log.info("{}|END", logPrefix);
            return received;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // NUT-11: mỗi dòng thành một lô (chính dòng phiếu), bút toán nhập = số lượng của dòng; rồi bù phần thiếu (P-38)
    private void confirmInto(StockReceipt receipt, Long userId) {
        receipt.confirm(userId, clock.instant());
        for (StockReceiptLine line : receipt.getLines()) {
            movementRepository.save(StockMovement.receipt(receipt, line, userId));
        }
        Collection<Long> foodIds =
                receipt.getLines().stream().map(StockReceiptLine::getFoodId).collect(Collectors.toSet());
        if (!foodIds.isEmpty()) {
            stockLedgerService.settleShortfalls(receipt.getCampusId(), foodIds, receipt.getId(), userId);
        }
        receiptRepository.flush();
    }

    private StockReceipt findReceipt(Long receiptId) {
        return receiptRepository
                .findWithLinesById(receiptId)
                .orElseThrow(() -> GlobalException.of(ApiCode.STOCK_RECEIPT_NOT_FOUND));
    }

    private StockReceipt findDraftForManage(CurrentUser currentUser, Long receiptId) {
        StockReceipt receipt = findReceipt(receiptId);
        checkCanManage(currentUser, receipt.getCampusId());
        if (!receipt.isDraft()) {
            throw GlobalException.of(ApiCode.STOCK_RECEIPT_STATUS_INVALID);
        }
        return receipt;
    }

    // AUTH-20: STOCK_MANAGE theo điểm trường (HT mọi điểm trường; PHT điểm trường được giao hoặc cả trường)
    private void checkCanManage(CurrentUser currentUser, Long campusId) {
        if (!campusAccessPolicy.canEditCampus(currentUser, Permission.STOCK_MANAGE, campusId)) {
            throw GlobalException.of(ApiCode.STOCK_ACCESS_DENIED);
        }
    }

    private void apply(StockReceipt receipt, StockReceiptRequest request, Long userId) {
        if (!supplierRepository.existsById(request.supplierId())) {
            throw GlobalException.of(ApiCode.SUPPLIER_NOT_FOUND);
        }
        Map<Long, Food> foods = foodRepository
                .findAllById(request.lines().stream()
                        .map(StockReceiptRequest.ReceiptLineInput::foodId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
        List<StockReceiptLine> lines = request.lines().stream()
                .map(line -> toLine(line, foods.get(line.foodId()), request.receivedDate()))
                .toList();
        String note = request.note() == null ? "" : request.note().trim();
        receipt.changeDraft(request.supplierId(), request.receivedDate(), note, lines, userId);
    }

    private static StockReceiptLine toLine(
            StockReceiptRequest.ReceiptLineInput line, Food food, LocalDate receivedDate) {
        if (food == null) {
            throw GlobalException.of(ApiCode.FOOD_NOT_FOUND);
        }
        // NUT-10: chỉ hàng kho khô vào kho; hàng tươi không cộng kho (NUT-12)
        if (food.getStorage() != FoodStorage.DRY || food.getStatus() != CatalogStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.STOCK_FOOD_NOT_DRY, food.getName());
        }
        String lotNo = line.lotNo().trim();
        if (line.expiryDate().isBefore(receivedDate)) {
            throw GlobalException.of(ApiCode.STOCK_EXPIRY_BEFORE_RECEIPT, lotNo);
        }
        return StockReceiptLine.of(food.getId(), lotNo, line.expiryDate(), line.quantity(), line.unitPrice());
    }

    private StockReceiptResponse toResponse(CurrentUser currentUser, StockReceipt receipt) {
        return toResponses(currentUser, List.of(receipt)).getFirst();
    }

    private List<StockReceiptResponse> toResponses(CurrentUser currentUser, List<StockReceipt> receipts) {
        if (receipts.isEmpty()) {
            return List.of();
        }
        Map<Long, Food> foods = foodRepository
                .findAllById(receipts.stream()
                        .flatMap(r -> Stream.concat(
                                r.getLines().stream().map(StockReceiptLine::getFoodId),
                                r.getRejectedLines().stream().map(StockRejectedLine::foodId)))
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
        Map<Long, String> suppliers =
                supplierRepository
                        .findAllById(receipts.stream()
                                .map(StockReceipt::getSupplierId)
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        Map<Long, Boolean> manage = receipts.stream()
                .map(StockReceipt::getCampusId)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> campusAccessPolicy.canEditCampus(currentUser, Permission.STOCK_MANAGE, id)));
        return receipts.stream()
                .map(r -> mapper.toResponse(r, suppliers.get(r.getSupplierId()), foods, manage.get(r.getCampusId())))
                .toList();
    }
}
