package com.trilong.kpibackend.modules.event.service;

import com.trilong.kpibackend.modules.event.dto.EventDTO;
import com.trilong.kpibackend.modules.event.entity.Event;
import com.trilong.kpibackend.modules.event.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public EventDTO createEvent(EventDTO dto) {
        Event event = new Event();
        mapEventDtoToEntity(dto, event);
        return toDTO(eventRepository.save(event));
    }

    @Transactional
    public EventDTO updateEvent(Long id, EventDTO dto) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + id));
        mapEventDtoToEntity(dto, event);
        return toDTO(eventRepository.save(event));
    }

    @Transactional
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
    }

    private void mapEventDtoToEntity(EventDTO dto, Event event) {
        event.setTitle(dto.getTitle());
        if (dto.getSlug() != null) {
            event.setSlug(dto.getSlug());
        } else if (event.getSlug() == null && dto.getTitle() != null) {
            event.setSlug(generateSlug(dto.getTitle()));
        }
        event.setEventType(dto.getEventType());
        event.setStatus(dto.getStatus());
        event.setBannerImage(dto.getBannerImage());
        event.setDescription(dto.getDescription());
        event.setLocation(dto.getLocation());
        event.setStartTime(dto.getStartTime());
        event.setEndTime(dto.getEndTime());
        event.setProjectId(dto.getProjectId());
        event.setGalleryImages(dto.getGalleryImages());
        
        if (dto.getParticipantCount() != null) {
            event.setParticipantCount(dto.getParticipantCount());
        } else if (event.getParticipantCount() == null) {
            event.setParticipantCount(0);
        }
        
        if (dto.getCheckinCount() != null) {
            event.setCheckinCount(dto.getCheckinCount());
        } else if (event.getCheckinCount() == null) {
            event.setCheckinCount(0);
        }
    }

    private String generateSlug(String input) {
        if (input == null) return null;
        String slug = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug + "-" + System.currentTimeMillis();
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
