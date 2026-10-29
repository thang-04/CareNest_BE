package com.carenest.dto.child;

public record ChildAddressInput(String province, String ward, String street) {

    @Override
    public String toString() {
        return "ChildAddressInput[]";
    }
}
