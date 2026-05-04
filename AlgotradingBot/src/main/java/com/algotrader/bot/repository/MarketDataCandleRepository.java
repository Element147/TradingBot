package com.algotrader.bot.repository;

import com.algotrader.bot.entity.MarketDataCandle;
import com.algotrader.bot.entity.MarketDataCandleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MarketDataCandleRepository extends JpaRepository<MarketDataCandle, MarketDataCandleId> {

    @Query("""
        select new com.algotrader.bot.repository.MarketDataCandleDto(
            candle.id.bucketStart,
            series.symbolDisplay,
            candle.openPrice,
            candle.highPrice,
            candle.lowPrice,
            candle.closePrice,
            candle.volume,
            dataset.id,
            importJob.id,
            segment.id,
            series.id,
            series.providerId,
            series.exchangeId,
            candle.id.timeframe,
            segment.resolutionTier,
            segment.sourceType,
            segment.coverageStart,
            segment.coverageEnd
        )
        from MarketDataCandle candle
        join candle.series series
        join candle.segment segment
        join segment.dataset dataset
        left join segment.importJob importJob
        where candle.id.seriesId = :seriesId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
        order by candle.id.bucketStart asc
        """)
    List<MarketDataCandleDto> findCandlesInRange(
        @Param("seriesId") Long seriesId,
        @Param("timeframe") String timeframe,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    @Query("""
        select new com.algotrader.bot.repository.MarketDataCandleDto(
            candle.id.bucketStart,
            series.symbolDisplay,
            candle.openPrice,
            candle.highPrice,
            candle.lowPrice,
            candle.closePrice,
            candle.volume,
            dataset.id,
            importJob.id,
            segment.id,
            series.id,
            series.providerId,
            series.exchangeId,
            candle.id.timeframe,
            segment.resolutionTier,
            segment.sourceType,
            segment.coverageStart,
            segment.coverageEnd
        )
        from MarketDataCandle candle
        join candle.series series
        join candle.segment segment
        join segment.dataset dataset
        left join segment.importJob importJob
        where dataset.id = :datasetId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
        order by candle.id.bucketStart asc, series.symbolNormalized asc
        """)
    List<MarketDataCandleDto> findDatasetCandlesInRange(
        @Param("datasetId") Long datasetId,
        @Param("timeframe") String timeframe,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    @Query("""
        select new com.algotrader.bot.repository.MarketDataCandleDto(
            candle.id.bucketStart,
            series.symbolDisplay,
            candle.openPrice,
            candle.highPrice,
            candle.lowPrice,
            candle.closePrice,
            candle.volume,
            dataset.id,
            importJob.id,
            segment.id,
            series.id,
            series.providerId,
            series.exchangeId,
            candle.id.timeframe,
            segment.resolutionTier,
            segment.sourceType,
            segment.coverageStart,
            segment.coverageEnd
        )
        from MarketDataCandle candle
        join candle.series series
        join candle.segment segment
        join segment.dataset dataset
        left join segment.importJob importJob
        where dataset.id = :datasetId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
          and (upper(series.symbolDisplay) = upper(:symbol) or upper(series.symbolNormalized) = upper(:symbol))
        order by candle.id.bucketStart asc
        """)
    List<MarketDataCandleDto> findDatasetCandlesForSymbolInRange(
        @Param("datasetId") Long datasetId,
        @Param("timeframe") String timeframe,
        @Param("symbol") String symbol,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    @Query("""
        select new com.algotrader.bot.repository.MarketDataCandleDto(
            candle.id.bucketStart,
            series.symbolDisplay,
            candle.openPrice,
            candle.highPrice,
            candle.lowPrice,
            candle.closePrice,
            candle.volume,
            dataset.id,
            importJob.id,
            segment.id,
            series.id,
            series.providerId,
            series.exchangeId,
            candle.id.timeframe,
            segment.resolutionTier,
            segment.sourceType,
            segment.coverageStart,
            segment.coverageEnd
        )
        from MarketDataCandle candle
        join candle.series series
        join candle.segment segment
        join segment.dataset dataset
        left join segment.importJob importJob
        where dataset.id = :datasetId
          and candle.id.seriesId = :seriesId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
        order by candle.id.bucketStart asc
        """)
    List<MarketDataCandleDto> findDatasetSeriesCandlesInRange(
        @Param("datasetId") Long datasetId,
        @Param("seriesId") Long seriesId,
        @Param("timeframe") String timeframe,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );
}
