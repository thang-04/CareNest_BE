package com.carenest.controller.observation;

import com.carenest.dto.observation.TicketDtos.IssueTicketsRequest;
import com.carenest.dto.observation.TicketDtos.IssuedTicket;
import com.carenest.dto.observation.TicketDtos.TicketBoardResponse;
import com.carenest.dto.observation.TicketDtos.TicketOverviewResponse;
import com.carenest.dto.observation.TicketDtos.TicketSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.BehaviourTicketService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.TicketType;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Phiếu bé ngoan: GV lớp phát phiếu; GV, PHT, HT xem tổng hợp; PHT / HT xem tổng quan các lớp. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BehaviourTicketController {
    private static final String LOG_PREFIX = "[BehaviourTicketController]";

    private final BehaviourTicketService behaviourTicketService;

    @GetMapping("/assessment/behaviour-tickets/board")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<TicketBoardResponse>> getBoard(
            @RequestParam Long classId,
            @RequestParam(required = false) TicketType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getBoard|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        TicketBoardResponse board = behaviourTicketService.getBoard(currentUser, classId, type, periodStart);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get ticket board success", board);
    }

    @PostMapping("/assessment/behaviour-tickets")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<List<IssuedTicket>>> issue(
            @Valid @RequestBody IssueTicketsRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|issue|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        List<IssuedTicket> issued = behaviourTicketService.issue(currentUser, request);
        log.info("{}|issued={}|END", logPrefix, issued.size());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Issue tickets success", issued);
    }

    @GetMapping("/assessment/behaviour-tickets/summary")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<TicketSummaryResponse>> getSummary(
            @RequestParam Long classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSummary|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        TicketSummaryResponse summary = behaviourTicketService.getSummary(currentUser, classId, month);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get ticket summary success", summary);
    }

    @GetMapping("/assessment/behaviour-tickets/overview")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<TicketOverviewResponse>> getOverview(
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @RequestParam(required = false) Long campusId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getOverview|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        TicketOverviewResponse overview =
                behaviourTicketService.getOverview(currentUser, schoolYearId, month, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get ticket overview success", overview);
    }
}
