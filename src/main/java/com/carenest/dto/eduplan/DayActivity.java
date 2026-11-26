package com.carenest.dto.eduplan;

import java.util.List;

/**
 * Một dòng hoạt động trong giáo án ngày đã ban hành (EDU-37): tên dòng của bảng tuần và các hoạt động của ngày (thẻ đã xếp,
 * gõ tay, nội dung ô). Chỉ tên — không MT, không mã nội bộ; dùng cho màn phụ huynh.
 */
public record DayActivity(String label, List<String> items) {}
