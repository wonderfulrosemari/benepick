package com.benepick.recommendation.controller;

import com.benepick.recommendation.dto.CardExternalSyncResponse;
import com.benepick.recommendation.dto.CatalogSyncStatusResponse;
import com.benepick.recommendation.dto.CatalogSummaryResponse;
import com.benepick.recommendation.dto.FinlifeSyncResponse;
import com.benepick.recommendation.service.CatalogSyncService;
import com.benepick.recommendation.service.CatalogSyncStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogSyncService catalogSyncService;
    private final CatalogSyncStatusService catalogSyncStatusService;

    public CatalogController(CatalogSyncService catalogSyncService, CatalogSyncStatusService catalogSyncStatusService) {
        this.catalogSyncService = catalogSyncService;
        this.catalogSyncStatusService = catalogSyncStatusService;
    }

    @GetMapping("/summary")
    public CatalogSummaryResponse getSummary() {
        return catalogSyncService.getCatalogSummary();
    }

    @GetMapping("/sync/status")
    public CatalogSyncStatusResponse getSyncStatus() {
        return catalogSyncStatusService.getSyncStatus();
    }

    @PostMapping("/sync/finlife")
    public FinlifeSyncResponse syncFinlife() {
        return catalogSyncStatusService.syncFinlifeWithStatus("manual-api");
    }

    @PostMapping("/sync/cards/external")
    public CardExternalSyncResponse syncExternalCards() {
        return catalogSyncStatusService.syncCardsWithStatus("manual-api");
    }
}
