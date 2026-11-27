package com.carenest.service.observation.impl;

import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.service.child.ChildDayUsageCheck;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssessmentChildDayUsageCheck implements ChildDayUsageCheck {
    private final DailyObservationRepository observations;
    private final DailyActivityResultRepository activities;

    @Override
    public boolean hasData(Long childId, LocalDate date) {
        return !observations.findByChildIdInAndDate(List.of(childId), date).isEmpty()
                || !activities.findByChildIdInAndDate(List.of(childId), date).isEmpty();
    }
}
