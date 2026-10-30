package com.carenest.dto.observation;

import com.carenest.utils.enums.TicketType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Phiếu bé ngoan: bảng phát phiếu của GV, tổng hợp theo trẻ, tổng quan các lớp cho PHT / HT. */
public final class TicketDtos {

    private TicketDtos() {}

    public record TicketClassRef(Long id, String name) {}

    /** weekNo: tuần thứ mấy của năm học (chỉ phiếu tuần). */
    public record TicketPeriod(LocalDate start, LocalDate end, Integer weekNo) {}

    public record TicketChild(Long id, String fullName) {}

    public record IssuedTicket(
            Long id,
            TicketType type,
            Long childId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer presentDays,
            Integer flagCount,
            Integer weeklyTickets,
            Long issuedBy,
            Instant issuedAt) {}

    /** Phiếu tuần có presentDays / flagCount; phiếu tháng có weeklyTickets. */
    public record TicketBoardRow(
            TicketChild child,
            Integer presentDays,
            Integer flagCount,
            Integer weeklyTickets,
            boolean eligible,
            IssuedTicket ticket) {}

    /** periods: các kỳ đã kết thúc, mới nhất trước; period null khi chưa có kỳ nào kết thúc. */
    public record TicketBoardResponse(
            TicketClassRef cls,
            TicketType type,
            List<TicketPeriod> periods,
            TicketPeriod period,
            List<TicketBoardRow> rows,
            Map<String, Integer> rules) {}

    public record IssueTicketsRequest(
            @NotNull Long classId,
            @NotNull TicketType type,
            @NotNull LocalDate periodStart,
            @NotNull @Size(min = 1, max = 100) List<@NotNull Long> childIds) {}

    public record TicketSummaryRow(TicketChild child, int weekly, int monthly, int flags) {}

    public record TicketSummaryTotals(int weekly, int monthly, int children, int total) {}

    /** month: ngày 1 của tháng đang xem, null = cả năm học; months: các tháng đã qua của năm học, mới nhất trước. */
    public record TicketSummaryResponse(
            TicketClassRef cls,
            LocalDate month,
            List<LocalDate> months,
            List<TicketSummaryRow> rows,
            TicketSummaryTotals totals) {}

    public record TicketOverviewRow(
            Long classId, String className, Long campusId, int children, int weekly, int monthly, int withTicket) {}

    public record TicketOverviewTotals(int weekly, int monthly, int withTicket, int children, int classes) {}

    public record TicketOverviewResponse(
            LocalDate month,
            List<LocalDate> months,
            List<Long> campusIds,
            List<TicketOverviewRow> rows,
            TicketOverviewTotals totals) {}
}
