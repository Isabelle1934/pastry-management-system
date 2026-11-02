package br.com.confeitaria.modules.schedule.repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.schedule.entity.WeeklySlot;

public interface WeeklySlotRepository extends JpaRepository<WeeklySlot, Long> {

    List<WeeklySlot> findAllByOrderByDayOfWeekAscStartTimeAsc();

    boolean existsByDayOfWeekAndStartTime(short dayOfWeek, LocalTime startTime);

    Optional<WeeklySlot> findByDayOfWeekAndStartTime(short dayOfWeek, LocalTime startTime);
}
