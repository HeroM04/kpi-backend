package com.trilong.kpibackend.modules.event.service;

import com.trilong.kpibackend.modules.event.dto.EventDTO;
import com.trilong.kpibackend.modules.event.entity.Event;
import com.trilong.kpibackend.modules.event.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    /**
     * Danh sách sự kiện có lọc (loại / trạng thái / từ khoá / khoảng ngày).
     * Tham số null = bỏ qua điều kiện đó.
     */
    public List<EventDTO> getEvents(String type, String status, String q, LocalDate from, LocalDate to) {
        String keyword = q == null ? null : q.trim().toLowerCase();
        return eventRepository.findAllByOrderByStartTimeDesc().stream()
                .filter(e -> type == null || type.equalsIgnoreCase(e.getEventType()))
                .filter(e -> status == null || status.equalsIgnoreCase(e.getStatus()))
                .filter(e -> keyword == null || keyword.isEmpty()
                        || (e.getTitle() != null && e.getTitle().toLowerCase().contains(keyword))
                        || (e.getLocation() != null && e.getLocation().toLowerCase().contains(keyword)))
                .filter(e -> from == null || (e.getStartTime() != null
                        && !e.getStartTime().toLocalDate().isBefore(from)))
                .filter(e -> to == null || (e.getStartTime() != null
                        && !e.getStartTime().toLocalDate().isAfter(to)))
                .map(this::toDTO)
                .toList();
    }

    public EventDTO getById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + id));
        return toDTO(event);
    }

    private EventDTO toDTO(Event e) {
        EventDTO dto = new EventDTO();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setSlug(e.getSlug());
        dto.setEventType(e.getEventType());
        dto.setStatus(e.getStatus());
        dto.setBannerImage(e.getBannerImage());
        dto.setDescription(e.getDescription());
        dto.setLocation(e.getLocation());
        dto.setStartTime(e.getStartTime());
        dto.setEndTime(e.getEndTime());
        dto.setProjectId(e.getProjectId());
        dto.setGalleryImages(e.getGalleryImages());
        dto.setParticipantCount(e.getParticipantCount());
        dto.setCheckinCount(e.getCheckinCount());
        return dto;
    }
}
