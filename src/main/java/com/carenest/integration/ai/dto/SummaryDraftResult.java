package com.carenest.integration.ai.dto;

/** Nháp hợp lệ kèm metadata để lưu vết (ADR-0008: source, model, prompt version). */
public record SummaryDraftResult(SummaryDraftContent content, String provider, String model, String promptVersion) {}
