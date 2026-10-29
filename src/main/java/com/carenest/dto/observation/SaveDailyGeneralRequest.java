package com.carenest.dto.observation;

import com.carenest.utils.enums.EatingLevel;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** View Hoạt động chung: sức khỏe, ăn, ngủ trưa, cảm xúc, hành vi, cờ (1 / ngày), nhận xét của từng trẻ. */
public record SaveDailyGeneralRequest(
        @NotNull LocalDate date, @NotNull @Valid List<Entry> entries, String classComment) {

    public SaveDailyGeneralRequest(LocalDate date, List<Entry> entries) {
        this(date, entries, null);
    }

    public record Entry(
            @NotNull Long childId,
            boolean healthOverridden,
            List<HealthSymptom> healthSigns,
            @Size(max = 500) String healthNote,
            Map<MealSession, EatingLevel> meals,
            NapLevel nap,
            Emotion emotion,
            Participation participation,
            List<ObservedBehavior> behaviors,
            @Size(max = 500) String behaviorNote,
            Boolean flag,
            @Size(max = 500) String comment) {}
}
