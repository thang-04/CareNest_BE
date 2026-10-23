package com.carenest.mapper.organization;

import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.StaffBriefResponse;
import com.carenest.entity.organization.Campus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CampusMapper {

    public CampusResponse toResponse(
            Campus campus, long classCount, long childCount, List<StaffBriefResponse> vicePrincipals) {
        return new CampusResponse(
                campus.getId(),
                campus.getCode(),
                campus.getName(),
                campus.getShortName(),
                campus.getAddress(),
                campus.getPhone(),
                campus.isMain(),
                campus.getStatus(),
                classCount,
                childCount,
                vicePrincipals);
    }
}
