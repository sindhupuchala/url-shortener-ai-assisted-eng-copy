package com.urlshortener.orchestration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrchestrationRunRepository extends JpaRepository<OrchestrationRunRecord, String> {
    List<OrchestrationRunRecord> findAllByOrderByCreatedAtDesc();
}