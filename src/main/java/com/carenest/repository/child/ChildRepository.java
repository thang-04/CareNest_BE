package com.carenest.repository.child;

import com.carenest.entity.child.Child;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {
}
