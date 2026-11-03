package com.carenest.service.meals;

import com.carenest.dto.meals.MenuTemplateRequest;
import com.carenest.dto.meals.MenuTemplateResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.MenuBlock;
import java.util.List;

/** Bộ thực đơn mẫu 4 tuần theo khối (NUT-13); kế hoạch tuần sao một tuần của bộ ở phía người lập. */
public interface MenuTemplateService {

    List<MenuTemplateResponse> getTemplates(CurrentUser currentUser, MenuBlock block, CatalogStatus status);

    MenuTemplateResponse getTemplate(CurrentUser currentUser, Long templateId);

    MenuTemplateResponse createTemplate(CurrentUser currentUser, MenuTemplateRequest request);

    MenuTemplateResponse updateTemplate(CurrentUser currentUser, Long templateId, MenuTemplateRequest request);

    MenuTemplateResponse changeTemplateStatus(CurrentUser currentUser, Long templateId, CatalogStatus status);
}
