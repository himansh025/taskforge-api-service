package com.taskforge.api.repository;

import com.taskforge.api.entity.ApiOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApiOutboxRepository extends JpaRepository<ApiOutboxEntity, UUID> {
    List<ApiOutboxEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
