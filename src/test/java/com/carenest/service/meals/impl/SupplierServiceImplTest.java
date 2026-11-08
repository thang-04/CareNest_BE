package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.dto.meals.SupplierResponse;
import com.carenest.entity.meals.Supplier;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SupplierServiceImplTest {

    private final SupplierRepository repository = mock(SupplierRepository.class);
    private final SupplierServiceImpl service = new SupplierServiceImpl(repository, new NutritionCatalogMapper());

    @Test
    @DisplayName("NUT-12: CCCD nhà cung cấp chỉ trả cho người có quyền sửa danh mục")
    void idCardHiddenFromViewOnlyUsers() {
        given(repository.findAllByOrderByNameAsc()).willReturn(List.of(supplier()));
        CurrentUser viewer = new CurrentUser(4L, "kc-4", "vp.b", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL));
        CurrentUser manager = new CurrentUser(
                3L,
                "kc-3",
                "vp.a",
                "Demo",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                Set.of(Permission.NUTRITION_MANAGE));

        assertThat(service.getSuppliers(viewer, null, null))
                .extracting(SupplierResponse::idCard)
                .containsOnlyNulls();
        assertThat(service.getSuppliers(manager, null, null))
                .extracting(SupplierResponse::idCard)
                .containsExactly("012345678901");
    }

    private static Supplier supplier() {
        Supplier supplier = Supplier.create(3L);
        supplier.changeInfo("NCC Rau sạch", "0912000000", "", "012345678901", "", 3L);
        return supplier;
    }
}
