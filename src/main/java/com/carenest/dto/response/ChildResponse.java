package com.carenest.dto.response;

import com.carenest.entity.Child;

import java.time.LocalDate;

public record ChildResponse(
        Long id,
        String fullName,
        LocalDate dateOfBirth,
        // Presigned URL of the child's photo, or null.
        String photoUrl,
        ParentInfo parent,
        // True when this request created a new parent account (default password = phone number).
        boolean parentAccountCreated
) {
    public record ParentInfo(Long id, String fullName, String phoneNumber) {
    }

    public static ChildResponse from(Child child, String photoUrl, boolean parentAccountCreated) {
        return new ChildResponse(
                child.getId(),
                child.getFullName(),
                child.getDateOfBirth(),
                photoUrl,
                new ParentInfo(
                        child.getParent().getId(),
                        child.getParent().getFullName(),
                        child.getParent().getPhoneNumber()),
                parentAccountCreated
        );
    }
}
