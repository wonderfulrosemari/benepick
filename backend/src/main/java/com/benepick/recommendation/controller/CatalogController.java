package com.benepick.recommendation.controller;

import com.benepick.recommendation.dto.CardExternalSyncResponse;
import com.benepick.recommendation.dto.CatalogSummaryResponse;
import com.benepick.recommendation.dto.FinlifeSyncResponse;
import com.benepick.recommendation.service.CatalogSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogSyncService catalogSyncService;

    public CatalogController(CatalogSyncService catalogSyncService) {
        this.catalogSyncService = catalogSyncService;
    }

    @GetMapping("/summary")
    public CatalogSummaryResponse getSummary() {
        return catalogSyncService.getCatalogSummary();
    }

    @PostMapping("/sync/finlife")
    public FinlifeSyncResponse syncFinlife() {
        return catalogSyncService.syncAccountsFromFinlife();
    }

    @PostMapping("/sync/cards/external")
    public CardExternalSyncResponse syncExternalCards() {
        return catalogSyncService.syncCardsFromExternal();
    }
}
