package com.paritosh.photosmigrator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.paritosh.photosmigrator.model.PickedMediaItem;
import com.paritosh.photosmigrator.service.PickerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/picker")
public class PickerController {
    private final PickerService picker;

    public PickerController(PickerService picker) {
        this.picker = picker;
    }

    @PostMapping("/sessions")
    public JsonNode createSession(@RequestBody(required = false) CreateSessionRequest request) {
        int max = request == null || request.maxItemCount() == null ? 2000 : request.maxItemCount();
        return picker.createSession(max);
    }

    @GetMapping("/sessions/{sessionId}")
    public JsonNode getSession(@PathVariable String sessionId) {
        return picker.getSession(sessionId);
    }

    @GetMapping("/sessions/{sessionId}/items")
    public List<PickedMediaItem> items(@PathVariable String sessionId) {
        return picker.listAll(sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        picker.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    public record CreateSessionRequest(Integer maxItemCount) { }
}
