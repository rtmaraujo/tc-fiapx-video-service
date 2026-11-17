package com.fiapx.video.infrastructure.repository;

import com.fiapx.video.infrastructure.persistence.OutboxEventoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventoRepository extends JpaRepository<OutboxEventoEntity, UUID> {

    List<OutboxEventoEntity> findTop50ByStatusOrderByCriadoEmAsc(OutboxEventoEntity.Status status);
}
