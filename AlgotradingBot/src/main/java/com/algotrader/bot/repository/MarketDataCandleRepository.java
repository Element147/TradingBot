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
        select candle
        from MarketDataCandle candle
        join fetch candle.series series
        join fetch candle.segment segment
        join fetch segment.dataset dataset
        left join fetch segment.importJob importJob
        where candle.id.seriesId = :seriesId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
        order by candle.id.bucketStart asc
        """)
    List<MarketDataCandle> findCandlesInRange(
        @Param("seriesId") Long seriesId,
        @Param("timeframe") String timeframe,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    @Query("""
        select candle
        from MarketDataCandle candle
        join fetch candle.series series
        join fetch candle.segment segment
        join fetch segment.dataset dataset
        left join fetch segment.importJob importJob
        where segment.dataset.id = :datasetId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
        order by candle.id.bucketStart asc, series.symbolNormalized asc
        """)
    List<MarketDataCandle> findDatasetCandlesInRange(
        @Param("datasetId") Long datasetId,
        @Param("timeframe") String timeframe,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    @Query("""
        select candle
        from MarketDataCandle candle
        join fetch candle.series series
        join fetch candle.segment segment
        join fetch segment.dataset dataset
        left join fetch segment.importJob importJob
        where segment.dataset.id = :datasetId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
          and (upper(series.symbolDisplay) = upper(:symbol) or upper(series.symbolNormalized) = upper(:symbol))
        order by candle.id.bucketStart asc
        """)
    List<MarketDataCandle> findDatasetCandlesForSymbolInRange(
        @Param("datasetId") Long datasetId,
        @Param("timeframe") String timeframe,
        @Param("symbol") String symbol,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );

    @Query("""
        select candle
        from MarketDataCandle candle
        join fetch candle.series series
        join fetch candle.segment segment
        join fetch segment.dataset dataset
        left join fetch segment.importJob importJob
        where segment.dataset.id = :datasetId
          and candle.id.seriesId = :seriesId
          and candle.id.timeframe = :timeframe
          and candle.id.bucketStart between :windowStart and :windowEnd
        order by candle.id.bucketStart asc
        """)
    List<MarketDataCandle> findDatasetSeriesCandlesInRange(
        @Param("datasetId") Long datasetId,
        @Param("seriesId") Long seriesId,
        @Param("timeframe") String timeframe,
        @Param("windowStart") LocalDateTime windowStart,
        @Param("windowEnd") LocalDateTime windowEnd
    );
}
