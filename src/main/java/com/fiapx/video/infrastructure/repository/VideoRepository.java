package com.fiapx.video.infrastructure.repository;

import com.fiapx.video.domain.VideoStatus;
import com.fiapx.video.infrastructure.persistence.VideoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<VideoEntity, UUID> {

    Page<VideoEntity> findByUsuarioId(UUID usuarioId, Pageable pageable);

    Page<VideoEntity> findByUsuarioIdAndStatus(UUID usuarioId, VideoStatus status, Pageable pageable);

    Optional<VideoEntity> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}
