package com.carenest.controller;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller chỉ dùng trong test để kiểm hợp đồng response; nằm package controller để nhận prefix API. */
@RestController
@RequestMapping("/response-contract")
class ResponseContractTestController {

    record SampleRequest(@NotBlank String name, @NotNull Long classId) {
    }

    @GetMapping("/data")
    ResponseEntity<ResponseJson<Map<String, Object>>> getData() {
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get data success", Map.of("id", 123));
    }

    @GetMapping("/no-data")
    ResponseEntity<ResponseJson<Void>> getNoData() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Update success");
    }

    @PostMapping("/validate")
    ResponseEntity<ResponseJson<SampleRequest>> validate(@Valid @RequestBody SampleRequest request) {
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Created", request);
    }

    @GetMapping("/not-found")
    ResponseEntity<ResponseJson<Void>> throwNotFound() {
        throw new GlobalException(ApiCode.NOT_FOUND, "Child not found");
    }

    @GetMapping("/conflict")
    ResponseEntity<ResponseJson<Void>> throwConflict() {
        throw new GlobalException(ApiCode.CONFLICT, "Meal count already confirmed");
    }

    @GetMapping("/unexpected")
    ResponseEntity<ResponseJson<Void>> throwUnexpected() {
        throw new IllegalStateException("secret internal detail");
    }
}
