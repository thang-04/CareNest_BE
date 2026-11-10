package com.carenest.dto.organization;

/** Học kỳ đã có dữ liệu theo khoảng ngày (vd. đánh giá học kỳ) thì khóa ngày của học kỳ đó (OBS-25). */
public record SemesterLockResponse(boolean semester1Locked, boolean semester2Locked) {}
