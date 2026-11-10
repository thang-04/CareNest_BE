package com.carenest.dto.child;

/** Tiếp xúc người bệnh lao: ngày gần nhất (yyyy-MM-dd) bắt buộc khi REPORTED. */
public record TbContactInput(String state, String contactDate) {}
