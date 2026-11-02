package com.carenest.service.observation;

import com.carenest.dto.observation.TicketDtos.IssueTicketsRequest;
import com.carenest.dto.observation.TicketDtos.IssuedTicket;
import com.carenest.dto.observation.TicketDtos.TicketBoardResponse;
import com.carenest.dto.observation.TicketDtos.TicketOverviewResponse;
import com.carenest.dto.observation.TicketDtos.TicketSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.TicketType;
import java.time.LocalDate;
import java.util.List;

/**
 * Phiếu bé ngoan (công thức giữ như FE TICKET_RULES): GV đang phụ trách lớp phát sau khi kỳ kết thúc cho trẻ đủ điều kiện;
 * GV và PHT / HT trong phạm vi xem tổng hợp; PHT / HT xem tổng quan các lớp.
 */
public interface BehaviourTicketService {

    TicketBoardResponse getBoard(CurrentUser currentUser, Long classId, TicketType type, LocalDate periodStart);

    List<IssuedTicket> issue(CurrentUser currentUser, IssueTicketsRequest request);

    TicketSummaryResponse getSummary(CurrentUser currentUser, Long classId, LocalDate month);

    TicketOverviewResponse getOverview(CurrentUser currentUser, Long schoolYearId, LocalDate month, Long campusId);
}
