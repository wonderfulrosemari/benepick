package com.benepick.recommendation.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "card_catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardCatalogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_key", nullable = false, unique = true, length = 80)
    private String productKey;

    @Column(name = "provider_name", nullable = false, length = 80)
    private String providerName;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "annual_fee_text", nullable = false, length = 120)
    private String annualFeeText;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "official_url", nullable = false, columnDefinition = "text")
    private String officialUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_catalog_tag", joinColumns = @JoinColumn(name = "card_catalog_id"))
    @Column(name = "tag_code", nullable = false, length = 30)
    private Set<String> tags = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_catalog_category", joinColumns = @JoinColumn(name = "card_catalog_id"))
    @Column(name = "category_code", nullable = false, length = 30)
    private Set<String> categories = new HashSet<>();

    public CardCatalogEntity(
        String productKey,
        String providerName,
        String productName,
        String annualFeeText,
        String summary,
        String officialUrl,
        boolean active,
        Set<String> tags,
        Set<String> categories
    ) {
        this.productKey = productKey;
        this.providerName = providerName;
        this.productName = productName;
        this.annualFeeText = annualFeeText;
        this.summary = summary;
        this.officialUrl = officialUrl;
        this.active = active;
        this.tags = new HashSet<>(tags);
        this.categories = new HashSet<>(categories);
    }

    public void refreshFromCatalog(
        String providerName,
        String productName,
        String annualFeeText,
        String summary,
        String officialUrl,
        Set<String> tags,
        Set<String> categories,
        boolean active
    ) {
        this.providerName = providerName;
        this.productName = productName;
        this.annualFeeText = annualFeeText;
        this.summary = summary;
        this.officialUrl = officialUrl;
        this.active = active;

        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList());
        }

        this.categories.clear();
        if (categories != null) {
            this.categories.addAll(categories.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList());
        }
    }

    public void deactivate() {
        this.active = false;
    }
}
