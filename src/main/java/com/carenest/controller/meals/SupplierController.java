package com.carenest.controller.meals;

import com.carenest.dto.meals.CatalogStatusRequest;
import com.carenest.dto.meals.SupplierRequest;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.SupplierService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.CatalogStatus;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục & thiết lập › Nhà cung cấp dùng chung hai điểm trường (NUT-12). */
@RestController
@RequestMapping("/nutrition/suppliers")
@RequiredArgsConstructor
@Slf4j
public class SupplierController {
    private static final String LOG_PREFIX = "[SupplierController]";

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<SupplierResponse>>> getSuppliers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CatalogStatus status,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSuppliers|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<SupplierResponse> suppliers = supplierService.getSuppliers(currentUser, keyword, status);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get suppliers success", suppliers);
    }

    @GetMapping("/{supplierId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<SupplierResponse>> getSupplier(
            @PathVariable Long supplierId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSupplier|userId=" + currentUser.userId() + "|supplierId=" + supplierId;
        log.info("{}|START", logPrefix);
        SupplierResponse supplier = supplierService.getSupplier(currentUser, supplierId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get supplier success", supplier);
    }

    @PostMapping
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<SupplierResponse>> createSupplier(
            @Valid @RequestBody SupplierRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createSupplier|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        SupplierResponse supplier = supplierService.createSupplier(currentUser, request);
        log.info("{}|supplierId={}|END", logPrefix, supplier.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create supplier success", supplier);
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<SupplierResponse>> updateSupplier(
            @PathVariable Long supplierId, @Valid @RequestBody SupplierRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateSupplier|userId=" + currentUser.userId() + "|supplierId=" + supplierId;
        log.info("{}|START", logPrefix);
        SupplierResponse supplier = supplierService.updateSupplier(currentUser, supplierId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update supplier success", supplier);
    }

    @PatchMapping("/{supplierId}/status")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<SupplierResponse>> changeSupplierStatus(
            @PathVariable Long supplierId, @Valid @RequestBody CatalogStatusRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|changeSupplierStatus|userId=" + currentUser.userId() + "|supplierId=" + supplierId;
        log.info("{}|START", logPrefix);
        SupplierResponse supplier = supplierService.changeSupplierStatus(currentUser, supplierId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Change supplier status success", supplier);
    }
}
