package com.algotrader.bot.repository;

import com.algotrader.bot.entity.MarketDataImportJob;
import com.algotrader.bot.service.marketdata.MarketDataImportJobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MarketDataImportJobRepository extends JpaRepository<MarketDataImportJob, Long> {

    List<MarketDataImportJob> findTop25ByOrderByCreatedAtDesc();

    @Query("""
        select job
        from MarketDataImportJob job
        where job.status in :statuses
          and (job.nextRetryAt is null or job.nextRetryAt <= :now)
        order by job.createdAt asc
        """)
    List<MarketDataImportJob> findReadyJobs(Collection<MarketDataImportJobStatus> statuses, LocalDateTime now, Pageable pageable);

    @Query("""
        select job
        from MarketDataImportJob job
        where job.status in :statuses
        order by job.createdAt asc
        """)
    List<MarketDataImportJob> findByStatusInOrderByCreatedAtAsc(Collection<MarketDataImportJobStatus> statuses);
}
