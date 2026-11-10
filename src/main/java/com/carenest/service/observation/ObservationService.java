package com.carenest.service.observation;

import com.carenest.dto.observation.ObservationCriteriaResponse;
import com.carenest.dto.observation.ObservationResponse;
import com.carenest.dto.observation.RecordObservationsRequest;
import com.carenest.dto.observation.RecordObservationsResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Quan sát hằng ngày có cấu trúc của trẻ (learning-observation). */
public interface ObservationService {

    List<ObservationCriteriaResponse> listCriteria(CurrentUser currentUser);

    RecordObservationsResponse recordObservations(
            CurrentUser currentUser, Long classroomId, RecordObservationsRequest request);

    List<ObservationResponse> listChildObservations(
            CurrentUser currentUser, Long childId, LocalDate from, LocalDate to);
}
