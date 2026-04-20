package com.algotrader.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_data_series", indexes = {
    @Index(name = "idx_market_series_provider_exchange_symbol", columnList = "provider_id,exchange_id,symbol_normalized"),
    @Index(name = "idx_market_series_asset_pair", columnList = "asset_class,base_asset,quote_asset")
}, uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_market_data_series_identity",
        columnNames = {
            "provider_id",
            "broker_id",
            "exchange_id",
            "asset_class",
            "instrument_type",
            "symbol_normalized",
            "base_asset",
            "quote_asset",
            "currency_code"
        }
    )
})
public class MarketDataSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 2, max = 40)
    @Column(name = "provider_id", nullable = false, length = 40)
    private String providerId;

    @NotNull
    @Size(max = 60)
    @Column(name = "broker_id", nullable = false, length = 60)
    private String brokerId;

    @NotNull
    @Size(max = 60)
    @Column(name = "exchange_id", nullable = false, length = 60)
    private String exchangeId;

    @NotNull
    @Size(min = 2, max = 30)
    @Column(name = "venue_type", nullable = false, length = 30)
    private String venueType;

    @NotNull
    @Size(min = 2, max = 30)
    @Column(name = "asset_class", nullable = false, length = 30)
    private String assetClass;

    @NotNull
    @Size(min = 2, max = 30)
    @Column(name = "instrument_type", nullable = false, length = 30)
    private String instrumentType;

    @NotNull
    @Size(min = 1, max = 80)
    @Column(name = "symbol_normalized", nullable = false, length = 80)
    private String symbolNormalized;

    @NotNull
    @Size(min = 1, max = 80)
    @Column(name = "symbol_display", nullable = false, length = 80)
    private String symbolDisplay;

    @NotNull
    @Size(max = 30)
    @Column(name = "base_asset", nullable = false, length = 30)
    private String baseAsset;

    @NotNull
    @Size(max = 30)
    @Column(name = "quote_asset", nullable = false, length = 30)
    private String quoteAsset;

    @NotNull
    @Size(max = 12)
    @Column(name = "currency_code", nullable = false, length = 12)
    private String currencyCode;

    @NotNull
    @Size(max = 8)
    @Column(name = "country_code", nullable = false, length = 8)
    private String countryCode;

    @NotNull
    @Size(min = 2, max = 64)
    @Column(name = "timezone_name", nullable = false, length = 64)
    private String timezoneName;

    @NotNull
    @Size(max = 40)
    @Column(name = "session_template", nullable = false, length = 40)
    private String sessionTemplate;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "provider_metadata_json")
    private String providerMetadataJson;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (brokerId == null) {
            brokerId = "";
        }
        if (exchangeId == null) {
            exchangeId = "";
        }
        if (baseAsset == null) {
            baseAsset = "";
        }
        if (quoteAsset == null) {
            quoteAsset = "";
        }
        if (currencyCode == null) {
            currencyCode = "";
        }
        if (countryCode == null) {
            countryCode = "";
        }
        if (sessionTemplate == null) {
            sessionTemplate = "";
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getBrokerId() {
        return brokerId;
    }

    public void setBrokerId(String brokerId) {
        this.brokerId = brokerId;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    public String getVenueType() {
        return venueType;
    }

    public void setVenueType(String venueType) {
        this.venueType = venueType;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public void setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
    }

    public String getSymbolNormalized() {
        return symbolNormalized;
    }

    public void setSymbolNormalized(String symbolNormalized) {
        this.symbolNormalized = symbolNormalized;
    }

    public String getSymbolDisplay() {
        return symbolDisplay;
    }

    public void setSymbolDisplay(String symbolDisplay) {
        this.symbolDisplay = symbolDisplay;
    }

    public String getBaseAsset() {
        return baseAsset;
    }

    public void setBaseAsset(String baseAsset) {
        this.baseAsset = baseAsset;
    }

    public String getQuoteAsset() {
        return quoteAsset;
    }

    public void setQuoteAsset(String quoteAsset) {
        this.quoteAsset = quoteAsset;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getTimezoneName() {
        return timezoneName;
    }

    public void setTimezoneName(String timezoneName) {
        this.timezoneName = timezoneName;
    }

    public String getSessionTemplate() {
        return sessionTemplate;
    }

    public void setSessionTemplate(String sessionTemplate) {
        this.sessionTemplate = sessionTemplate;
    }

    public String getProviderMetadataJson() {
        return providerMetadataJson;
    }

    public void setProviderMetadataJson(String providerMetadataJson) {
        this.providerMetadataJson = providerMetadataJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
