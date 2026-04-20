package com.algotrader.bot.repository;

import com.algotrader.bot.entity.OperatorAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface OperatorAuditEventRepository extends JpaRepository<OperatorAuditEvent, Long>, JpaSpecificationExecutor<OperatorAuditEvent> {
    List<OperatorAuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
