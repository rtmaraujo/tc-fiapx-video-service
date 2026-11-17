package com.fiapx.video.infrastructure.repository;

import com.fiapx.video.infrastructure.persistence.NotificacaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<NotificacaoEntity, UUID> {

    List<NotificacaoEntity> findTop20ByStatusOrderByCriadoEmAsc(NotificacaoEntity.Status status);
}
