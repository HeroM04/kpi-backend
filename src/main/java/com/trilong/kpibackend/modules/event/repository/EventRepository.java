package com.trilong.kpibackend.modules.event.repository;

import com.trilong.kpibackend.modules.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByStartTimeDesc();
}
