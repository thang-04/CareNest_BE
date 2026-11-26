package com.carenest.service.meals;

import com.carenest.dto.meals.DayBalanceRequest;
import com.carenest.dto.meals.DayBalanceResponse;
import com.carenest.dto.meals.DayBalanceStatusResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Bản cân đối khẩu phần theo ngày. */
public interface DayBalanceService {

    /** Trạng thái cân đối từng ngày học trong khoảng (tối đa 31 ngày). */
    List<DayBalanceStatusResponse> getStatuses(CurrentUser currentUser, LocalDate from, LocalDate to);

    /** Bản đã lưu của ngày; chưa lưu thì dựng từ công thức món và sĩ số báo ăn. */
    DayBalanceResponse getBalance(CurrentUser currentUser, LocalDate date, int round);

    /** Tính thử bản đang sửa (làm tròn, chấm lượng, chất, tiền) mà không lưu. */
    DayBalanceResponse previewBalance(CurrentUser currentUser, LocalDate date, int round, DayBalanceRequest request);

    DayBalanceResponse saveBalance(CurrentUser currentUser, LocalDate date, int round, DayBalanceRequest request);
}
