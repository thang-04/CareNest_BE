package com.carenest.dto.organization;

public record AgeGroupResponse(
        Long id, String code, String name, String shortName, Short sortOrder, Short ageFromMonths, Short ageToMonths) {
    public AgeGroupResponse(Long id, String code, String name, String shortName, Short sortOrder) {
        this(id, code, name, shortName, sortOrder, null, null);
    }
}
