package com.carenest.repository.meals;

import com.carenest.entity.meals.DayBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DayBalanceRepository extends JpaRepository<DayBalance, Long> {

    Optional<DayBalance> findByBalanceDateAndRound(LocalDate balanceDate, short round);

    boolean existsByBalanceDateAndRound(LocalDate balanceDate, short round);

    List<DayBalance> findByBalanceDateBetween(LocalDate from, LocalDate to);
}
