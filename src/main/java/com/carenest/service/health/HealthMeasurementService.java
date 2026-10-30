package com.carenest.service.health;

import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthMeasurementRequest;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.security.CurrentUser;

/** Số đo của trẻ trong kỳ cân đo (HLT-09, HLT-13): GV phụ trách lớp nhập, sửa có lý do; BGH, GV xem sổ sức khỏe. */
public interface HealthMeasurementService {

    ChildHealthRecordResponse getHealthRecord(CurrentUser currentUser, Long childId);

    HealthMeasurementResponse createMeasurement(
            CurrentUser currentUser, Long childId, HealthMeasurementRequest request);

    HealthMeasurementResponse updateMeasurement(
            CurrentUser currentUser, Long childId, Long measurementId, HealthMeasurementRequest request);
}
