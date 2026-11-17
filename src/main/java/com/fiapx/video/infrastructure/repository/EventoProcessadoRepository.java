package com.fiapx.video.infrastructure.repository;

import com.fiapx.video.infrastructure.persistence.EventoProcessadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcessadoRepository extends JpaRepository<EventoProcessadoEntity, String> {
}
