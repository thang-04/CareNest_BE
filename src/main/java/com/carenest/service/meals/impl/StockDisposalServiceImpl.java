package com.carenest.service.meals.impl;

import com.carenest.dto.meals.StockDisposalRequest;
import com.carenest.dto.meals.StockDisposalResponse;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.StockDisposal;
import com.carenest.entity.meals.StockLot;
import com.carenest.entity.meals.StockMovement;
import com.carenest.entity.meals.Supplier;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.StockMapper;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.StockDisposalRepository;
import com.carenest.repository.meals.StockLotRepository;
import com.carenest.repository.meals.StockMovementRepository;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.StockDisposalService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StockDisposalKind;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class StockDisposalServiceImpl implements StockDisposalService {
    private static final String LOG_PREFIX = "[StockDisposalServiceImpl]";
    private static final int MIN_REASON_LENGTH = 3;
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StockLotRepository lotRepository;
    private final StockMovementRepository movementRepository;
    private final StockDisposalRepository disposalRepository;
    private final FoodRepository foodRepository;
    private final SupplierRepository supplierRepository;
    private final CampusAccessPolicy campusAccessPolicy;
    private final StockMapper mapper;
    private final Clock clock;

    @Override
    public StockDisposalResponse createReturn(CurrentUser currentUser, StockDisposalRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createReturn|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        try {
            StockDisposalResponse response = create(currentUser, request, StockDisposalKind.SUPPLIER_RETURN);
            log.info("{}|disposalId={}|END", logPrefix, response.id());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public StockDisposalResponse createWriteOff(CurrentUser currentUser, StockDisposalRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createWriteOff|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        try {
            StockDisposalResponse response = create(currentUser, request, StockDisposalKind.WRITE_OFF);
            log.info("{}|disposalId={}|END", logPrefix, response.id());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private StockDisposalResponse create(
            CurrentUser currentUser, StockDisposalRequest request, StockDisposalKind kind) {
        Long campusId = request.campusId();
        if (!campusAccessPolicy.canEditCampus(currentUser, Permission.STOCK_MANAGE, campusId)) {
            throw GlobalException.of(ApiCode.STOCK_ACCESS_DENIED);
        }
        String reason = request.reason().trim();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw GlobalException.of(ApiCode.STOCK_DISPOSAL_REASON_TOO_SHORT);
        }
        boolean isReturn = kind == StockDisposalKind.SUPPLIER_RETURN;
        Supplier supplier = null;
        if (isReturn) {
            if (request.supplierId() == null) {
                throw GlobalException.of(ApiCode.STOCK_DISPOSAL_SUPPLIER_REQUIRED);
            }
            supplier = supplierRepository
                    .findById(request.supplierId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.SUPPLIER_NOT_FOUND));
        }
        // Khóa điểm trường để hai phiếu cùng lúc không cùng trừ một lô, và để đánh số phiếu không trùng
        lotRepository.lockCampus(campusId);
        Map<Long, StockLot> lots = findLots(campusId, request.lines());
        Map<Long, BigDecimal> balances = movementRepository.sumByLotIds(List.copyOf(lots.keySet())).stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.LotBalance::getLotId, StockMovementRepository.LotBalance::getBalance));
        LocalDate today = LocalDate.now(clock);
        for (StockDisposalRequest.DisposalLineInput line : request.lines()) {
            checkLine(lots.get(line.lotId()), line.quantity(), balances, today, kind, request.supplierId());
        }

        long sequence = disposalRepository.countByCampusIdAndDisposalDateAndKind(campusId, today, kind) + 1;
        String code = "%s-%s-%d-%02d".formatted(kind.codePrefix(), CODE_DATE.format(today), campusId, sequence);
        StockDisposal disposal = disposalRepository.saveAndFlush(StockDisposal.of(
                code, campusId, kind, supplier == null ? null : supplier.getId(), today, reason, currentUser.userId()));

        Map<Long, Food> foods =
                foodRepository
                        .findAllById(
                                lots.values().stream().map(StockLot::getFoodId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Food::getId, Function.identity()));
        List<StockDisposalResponse.DisposalLine> responseLines = new ArrayList<>();
        for (StockDisposalRequest.DisposalLineInput line : request.lines()) {
            StockLot lot = lots.get(line.lotId());
            movementRepository.save(StockMovement.disposal(lot, disposal, line.quantity(), currentUser.userId()));
            responseLines.add(mapper.toDisposalLine(lot, foods.get(lot.getFoodId()), line.quantity()));
        }
        return mapper.toResponse(disposal, supplier == null ? null : supplier.getName(), responseLines);
    }

    /** Lô của phiếu: mỗi lô một dòng, phải thuộc đúng điểm trường của phiếu. */
    private Map<Long, StockLot> findLots(Long campusId, List<StockDisposalRequest.DisposalLineInput> lines) {
        Set<Long> ids = new HashSet<>();
        for (StockDisposalRequest.DisposalLineInput line : lines) {
            if (!ids.add(line.lotId())) {
                throw GlobalException.of(ApiCode.STOCK_DISPOSAL_LOT_DUPLICATE);
            }
        }
        Map<Long, StockLot> lots =
                lotRepository.findAllById(ids).stream().collect(Collectors.toMap(StockLot::getId, Function.identity()));
        for (Long id : ids) {
            StockLot lot = lots.get(id);
            if (lot == null || !campusId.equals(lot.getCampusId())) {
                throw GlobalException.of(ApiCode.STOCK_DISPOSAL_LOT_NOT_FOUND);
            }
        }
        return lots;
    }

    // NUT-29: trả NCC chỉ lô còn hạn do đúng NCC giao; NUT-30: xuất hủy chỉ lô đã hết hạn (hạn dùng < ngày lập phiếu)
    private void checkLine(
            StockLot lot,
            BigDecimal quantity,
            Map<Long, BigDecimal> balances,
            LocalDate today,
            StockDisposalKind kind,
            Long supplierId) {
        boolean expired = lot.getExpiryDate().isBefore(today);
        if (kind == StockDisposalKind.SUPPLIER_RETURN) {
            if (expired) {
                throw GlobalException.of(ApiCode.STOCK_DISPOSAL_LOT_EXPIRED, lot.getLotNo());
            }
            if (!supplierId.equals(lot.getSupplierId())) {
                throw GlobalException.of(ApiCode.STOCK_DISPOSAL_SUPPLIER_MISMATCH, lot.getLotNo());
            }
        } else if (!expired) {
            throw GlobalException.of(ApiCode.STOCK_DISPOSAL_LOT_NOT_EXPIRED, lot.getLotNo());
        }
        BigDecimal balance = balances.getOrDefault(lot.getId(), BigDecimal.ZERO);
        if (quantity.compareTo(balance) > 0) {
            throw GlobalException.of(
                    ApiCode.STOCK_DISPOSAL_QUANTITY_EXCEEDED,
                    lot.getLotNo(),
                    balance.stripTrailingZeros().toPlainString());
        }
    }
}
