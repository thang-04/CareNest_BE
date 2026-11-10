package com.carenest.service.meals;

import com.carenest.dto.meals.SupplierRequest;
import com.carenest.dto.meals.SupplierResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CatalogStatus;
import java.util.List;

/** Nhà cung cấp dùng chung hai điểm trường (NUT-12). */
public interface SupplierService {

    List<SupplierResponse> getSuppliers(CurrentUser currentUser, String keyword, CatalogStatus status);

    SupplierResponse getSupplier(CurrentUser currentUser, Long supplierId);

    SupplierResponse createSupplier(CurrentUser currentUser, SupplierRequest request);

    SupplierResponse updateSupplier(CurrentUser currentUser, Long supplierId, SupplierRequest request);

    SupplierResponse changeSupplierStatus(CurrentUser currentUser, Long supplierId, CatalogStatus status);
}
