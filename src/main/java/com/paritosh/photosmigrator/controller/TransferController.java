package com.paritosh.photosmigrator.controller;

import com.paritosh.photosmigrator.model.TransferRunResult;
import com.paritosh.photosmigrator.service.TransferService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/migrations")
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/{migrationId}/transfer")
    public TransferRunResult transfer(@PathVariable String migrationId, @RequestBody TransferRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        int maxItems = request.maxItems() == null ? 25 : request.maxItems();
        return transferService.transfer(migrationId, request.sessionId(), maxItems);
    }

    public record TransferRequest(String sessionId, Integer maxItems) { }
}
