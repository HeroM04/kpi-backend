package com.trilong.kpibackend.modules.event.controller;

import com.trilong.kpibackend.modules.event.dto.EventDTO;
import com.trilong.kpibackend.modules.event.service.EventService;
import com.trilong.kpibackend.core.utils.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Event API", description = "Sự kiện cổng SaleWeb (Trí Long Land)")
public class EventController {

    @Autowired
    private EventService eventService;

    @Operation(summary = "Danh sách sự kiện (lọc loại/trạng thái/từ khoá/khoảng ngày + phân trang)")
    @GetMapping
    public ResponseEntity<?> getEvents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        List<EventDTO> events = eventService.getEvents(type, status, q, from, to);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", PageResponse.fromList(events, page, size)
        ));
    }

    @Operation(summary = "Chi tiết một sự kiện")
    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable Long id) {
        EventDTO event = eventService.getById(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "data", event
        ));
    }
}
