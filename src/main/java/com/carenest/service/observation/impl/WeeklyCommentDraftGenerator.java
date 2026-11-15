package com.carenest.service.observation.impl;

import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.Participation;
import java.util.List;
import java.util.Map;

/** Viết bản nháp nhận xét tuần cho một trẻ từ số liệu tuần (OBS-29); GV luôn đọc, sửa rồi mới gửi. */
interface WeeklyCommentDraftGenerator {

    /** Số liệu tuần của một trẻ, không có họ tên hay định danh: câu nháp gọi trẻ là "con". */
    record WeekFacts(
            int presentDays,
            int schoolDays,
            Map<Emotion, Integer> emotion,
            Map<Participation, Integer> participation,
            Map<NapLevel, Integer> nap,
            int lunchFinished,
            int lunchDays,
            List<String> metGoals,
            List<String> unmetGoals,
            int flagCount,
            boolean weeklyTicket) {}

    String write(WeekFacts facts);
}
