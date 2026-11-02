package br.com.confeitaria.modules.schedule.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.schedule.entity.ScheduleBlock;

public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, Long> {

    List<ScheduleBlock> findAllByBlockDateBetweenOrderByBlockDateAscStartTimeAsc(LocalDate from, LocalDate to);

    List<ScheduleBlock> findAllByBlockDateGreaterThanEqualOrderByBlockDateAscStartTimeAsc(LocalDate from);

    boolean existsByBlockDateAndStartTime(LocalDate date, LocalTime time);

    boolean existsByBlockDateAndStartTimeIsNull(LocalDate date);
}
