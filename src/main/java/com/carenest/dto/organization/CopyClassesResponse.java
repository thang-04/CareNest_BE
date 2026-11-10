package com.carenest.dto.organization;

import java.util.List;

public record CopyClassesResponse(List<String> copied, List<Skipped> skipped, List<String> withoutRoom) {

    public record Skipped(String name, String reason) {}
}
