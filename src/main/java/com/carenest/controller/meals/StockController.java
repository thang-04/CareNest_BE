package com.carenest.controller.meals;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.meals.StockLotResponse;
import com.carenest.dto.meals.StockMovementResponse;
import com.carenest.dto.meals.StockReceiptCancelRequest;
import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.dto.meals.StockReceiptResponse;
import com.carenest.dto.meals.StockShortfallResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.StockMovementType;
import com.carenest.utils.enums.StockReceiptStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Kho thực phẩm › kho khô theo điểm trường: phiếu nhập, tồn theo lô, lịch sử nhập/trừ, phần thiếu (NUT-11). */
@RestController
@RequestMapping("/nutrition/stock")
@RequiredArgsConstructor
@Slf4j
public class StockController {
    private static final String LOG_PREFIX = "[StockController]";

    private final StockReceiptService stockReceiptService;
    private final StockLedgerService stockLedgerService;

    @GetMapping("/receipts")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<StockReceiptResponse>>> getReceipts(
            @RequestParam Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) StockReceiptStatus status,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getReceipts|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<StockReceiptResponse> receipts = stockReceiptService.getReceipts(currentUser, campusId, from, to, status);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get stock receipts success", receipts);
    }

    @GetMapping("/receipts/{receiptId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<StockReceiptResponse>> getReceipt(
            @PathVariable Long receiptId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        StockReceiptResponse receipt = stockReceiptService.getReceipt(currentUser, receiptId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get stock receipt success", receipt);
    }

    @PostMapping("/receipts")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<StockReceiptResponse>> createReceipt(
            @Valid @RequestBody StockReceiptRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createReceipt|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        StockReceiptResponse receipt = stockReceiptService.createReceipt(currentUser, request);
        log.info("{}|receiptId={}|END", logPrefix, receipt.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create stock receipt success", receipt);
    }

    @PutMapping("/receipts/{receiptId}")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    public ResponseEntity<ResponseJson<StockReceiptResponse>> updateReceipt(
            @PathVariable Long receiptId, @Valid @RequestBody StockReceiptRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        StockReceiptResponse receipt = stockReceiptService.updateReceipt(currentUser, receiptId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update stock receipt success", receipt);
    }

    @DeleteMapping("/receipts/{receiptId}")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    public ResponseEntity<ResponseJson<Void>> deleteReceipt(@PathVariable Long receiptId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        stockReceiptService.deleteReceipt(currentUser, receiptId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete stock receipt success");
    }

    @PostMapping("/receipts/{receiptId}/confirm")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    public ResponseEntity<ResponseJson<StockReceiptResponse>> confirmReceipt(
            @PathVariable Long receiptId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|confirmReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        StockReceiptResponse receipt = stockReceiptService.confirmReceipt(currentUser, receiptId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Confirm stock receipt success", receipt);
    }

    @PostMapping("/receipts/{receiptId}/cancel")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    public ResponseEntity<ResponseJson<StockReceiptResponse>> cancelReceipt(
            @PathVariable Long receiptId,
            @Valid @RequestBody StockReceiptCancelRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|cancelReceipt|userId=" + currentUser.userId() + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        StockReceiptResponse receipt = stockReceiptService.cancelReceipt(currentUser, receiptId, request.reason());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Cancel stock receipt success", receipt);
    }

    @PostMapping("/days/{date}/sync")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<Void>> resyncDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long campusId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|resyncDay|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        stockLedgerService.resyncDay(currentUser, campusId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Resync stock day success");
    }

    @GetMapping("/lots")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<StockLotResponse>>> getLots(
            @RequestParam Long campusId,
            @RequestParam(required = false) Long foodId,
            @RequestParam(defaultValue = "false") boolean includeEmpty,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getLots|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<StockLotResponse> lots = stockLedgerService.getLots(currentUser, campusId, foodId, includeEmpty);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get stock lots success", lots);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<PageResponse<StockMovementResponse>>> getMovements(
            @RequestParam Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long foodId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMovements|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        PageResponse<StockMovementResponse> movements =
                stockLedgerService.getMovements(currentUser, campusId, from, to, foodId, type, page, size);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get stock movements success", movements);
    }

    @GetMapping("/shortfalls")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<StockShortfallResponse>>> getShortfalls(
            @RequestParam Long campusId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getShortfalls|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<StockShortfallResponse> shortfalls = stockLedgerService.getShortfalls(currentUser, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get stock shortfalls success", shortfalls);
    }
}
