package com.carenest.mapper.child;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.entity.child.Child;
import org.springframework.stereotype.Component;

@Component
public class ChildMapper {

    public ChildProfileResponse toProfileResponse(Child child, Long currentClassroomId) {
        return new ChildProfileResponse(child.getId(), child.getFullName(), child.getDateOfBirth(), currentClassroomId);
    }
}
