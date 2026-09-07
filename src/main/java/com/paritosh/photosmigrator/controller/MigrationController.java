package com.paritosh.photosmigrator.controller;

import com.paritosh.photosmigrator.model.MigrationItem;
import com.paritosh.photosmigrator.model.MigrationStatus;
import com.paritosh.photosmigrator.service.MigrationLedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/migrations")
public class MigrationController {
    private final MigrationLedgerService ledgerService;

    public MigrationController(MigrationLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/{migrationId}/initialize")
    public ResponseEntity<Void> initialize(@PathVariable String migrationId) {
        ledgerService.initialize(migrationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{migrationId}/items")
    public List<MigrationItem> items(@PathVariable String migrationId) {
        return ledgerService.items(migrationId);
    }

    @PutMapping("/{migrationId}/items")
    public ResponseEntity<Void> upsert(@PathVariable String migrationId, @RequestBody MigrationItem item) {
        ledgerService.upsert(migrationId, item);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{migrationId}/summary")
    public Map<MigrationStatus, Long> summary(@PathVariable String migrationId) {
        return ledgerService.summary(migrationId);
    }
}
