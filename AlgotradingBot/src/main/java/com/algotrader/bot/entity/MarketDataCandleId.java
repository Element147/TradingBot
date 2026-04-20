package com.algotrader.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Embeddable
public class MarketDataCandleId implements Serializable {

    @NotNull
    @Column(name = "series_id", nullable = false)
    private Long seriesId;

    @NotNull
    @Size(min = 2, max = 10)
    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @NotNull
    @Column(name = "bucket_start", nullable = false)
    private LocalDateTime bucketStart;

    public MarketDataCandleId() {
    }

    public MarketDataCandleId(Long seriesId, String timeframe, LocalDateTime bucketStart) {
        this.seriesId = seriesId;
        this.timeframe = timeframe;
        this.bucketStart = bucketStart;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public LocalDateTime getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(LocalDateTime bucketStart) {
        this.bucketStart = bucketStart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MarketDataCandleId that)) {
            return false;
        }
        return Objects.equals(seriesId, that.seriesId)
            && Objects.equals(timeframe, that.timeframe)
            && Objects.equals(bucketStart, that.bucketStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seriesId, timeframe, bucketStart);
    }
}
