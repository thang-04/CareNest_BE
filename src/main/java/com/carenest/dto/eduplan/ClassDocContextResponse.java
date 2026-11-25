package com.carenest.dto.eduplan;

import java.util.List;

/** Phần nhỏ của danh sách tài liệu lớp (lớp, nhân sự, quyền tạo) để trang danh sách không phải tải nội dung soạn. */
public record ClassDocContextResponse(
        List<ClassDocsResponse.ClassItem> classes,
        List<ClassDocsResponse.StaffItem> staff,
        List<ClassDocsResponse.CreationCapability> creationCapabilities,
        boolean semesterConfigMissing) {}
