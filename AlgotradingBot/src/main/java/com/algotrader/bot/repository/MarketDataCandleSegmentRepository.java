package com.algotrader.bot.repository;

import com.algotrader.bot.entity.MarketDataCandleSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketDataCandleSegmentRepository extends JpaRepository<MarketDataCandleSegment, Long> {

    boolean existsByDatasetId(Long datasetId);

    @Query("""
        select segment
        from MarketDataCandleSegment segment
        where segment.series.id = :seriesId
          and segment.timeframe = :timeframe
          and segment.coverageStart <= :requestedEnd
          and segment.coverageEnd >= :requestedStart
        order by segment.coverageStart asc, segment.createdAt asc
        """)
    List<MarketDataCandleSegment> findOverlappingSegments(
        @Param("seriesId") Long seriesId,
        @Param("timeframe") String timeframe,
        @Param("requestedStart") LocalDateTime requestedStart,
        @Param("requestedEnd") LocalDateTime requestedEnd
    );

    @Query("""
        select segment
        from MarketDataCandleSegment segment
        where segment.dataset.id = :datasetId
          and segment.series.id = :seriesId
          and segment.timeframe = :timeframe
          and segment.checksumSha256 = :checksumSha256
        """)
    Optional<MarketDataCandleSegment> findByDatasetSeriesTimeframeAndChecksum(
        @Param("datasetId") Long datasetId,
        @Param("seriesId") Long seriesId,
        @Param("timeframe") String timeframe,
        @Param("checksumSha256") String checksumSha256
    );

    @Query("""
        select segment
        from MarketDataCandleSegment segment
        join fetch segment.series series
        join fetch segment.dataset dataset
        left join fetch segment.importJob importJob
        where dataset.id = :datasetId
        order by series.symbolNormalized asc, segment.timeframe asc, segment.coverageStart asc, segment.createdAt asc
        """)
    List<MarketDataCandleSegment> findByDatasetIdWithSeries(@Param("datasetId") Long datasetId);
}
