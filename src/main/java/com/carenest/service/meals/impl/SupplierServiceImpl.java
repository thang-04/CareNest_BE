package com.carenest.service.meals.impl;

import com.carenest.dto.meals.SupplierRequest;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.entity.meals.Supplier;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.SupplierService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.Permission;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierServiceImpl implements SupplierService {
    private static final String LOG_PREFIX = "[SupplierServiceImpl]";

    private final SupplierRepository supplierRepository;
    private final NutritionCatalogMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> getSuppliers(CurrentUser currentUser, String keyword, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|getSuppliers|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
            boolean showIdCard = canSeeIdCard(currentUser);
            List<SupplierResponse> response = supplierRepository.findAllByOrderByNameAsc().stream()
                    .filter(supplier -> term.isEmpty()
                            || supplier.getName().toLowerCase(Locale.ROOT).contains(term)
                            || supplier.getPhone().contains(term))
                    .filter(supplier -> status == null || supplier.getStatus() == status)
                    .map(supplier -> mapper.toResponse(supplier, showIdCard))
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
    public SupplierResponse getSupplier(CurrentUser currentUser, Long supplierId) {
        String logPrefix = LOG_PREFIX + "|getSupplier|userId=" + currentUser.userId() + "|supplierId=" + supplierId;
        log.info("{}|START", logPrefix);
        try {
            SupplierResponse response = mapper.toResponse(findSupplier(supplierId), canSeeIdCard(currentUser));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SupplierResponse createSupplier(CurrentUser currentUser, SupplierRequest request) {
        String logPrefix = LOG_PREFIX + "|createSupplier|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            Supplier supplier = Supplier.create(currentUser.userId());
            apply(supplier, request, currentUser.userId());
            supplier = supplierRepository.save(supplier);
            log.info("{}|supplierId={}|END", logPrefix, supplier.getId());
            return mapper.toResponse(supplier, true);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SupplierResponse updateSupplier(CurrentUser currentUser, Long supplierId, SupplierRequest request) {
        String logPrefix = LOG_PREFIX + "|updateSupplier|userId=" + currentUser.userId() + "|supplierId=" + supplierId;
        log.info("{}|START", logPrefix);
        try {
            Supplier supplier = findSupplier(supplierId);
            apply(supplier, request, currentUser.userId());
            supplierRepository.flush();
            log.info("{}|END", logPrefix);
            return mapper.toResponse(supplier, true);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SupplierResponse changeSupplierStatus(CurrentUser currentUser, Long supplierId, CatalogStatus status) {
        String logPrefix = LOG_PREFIX + "|changeSupplierStatus|userId=" + currentUser.userId() + "|supplierId="
                + supplierId + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            Supplier supplier = findSupplier(supplierId);
            supplier.changeStatus(status, currentUser.userId());
            supplierRepository.flush();
            log.info("{}|END", logPrefix);
            return mapper.toResponse(supplier, true);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Supplier findSupplier(Long supplierId) {
        return supplierRepository
                .findById(supplierId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SUPPLIER_NOT_FOUND));
    }

    // NUT-12: CCCD là dữ liệu cá nhân, chỉ người quản lý danh mục được xem
    private static boolean canSeeIdCard(CurrentUser currentUser) {
        return currentUser.hasPermission(Permission.NUTRITION_MANAGE);
    }

    private static void apply(Supplier supplier, SupplierRequest request, Long userId) {
        supplier.changeInfo(
                request.name().trim(),
                request.phone().trim(),
                request.address() == null ? "" : request.address().trim(),
                request.idCard() == null || request.idCard().isBlank()
                        ? null
                        : request.idCard().trim(),
                request.deliveryPerson() == null ? "" : request.deliveryPerson().trim(),
                userId);
    }
}
