package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduSettings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EduSettingsRepository extends JpaRepository<EduSettings, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EduSettings s where s.id = 1")
    Optional<EduSettings> findForUpdate();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from EduSettings s where s.id = 1")
    Optional<EduSettings> findForShare();
}
