package com.wenxinblog.content.repository;

import com.wenxinblog.content.entity.MediaAsset;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface MediaAssetRepository extends ReactiveCrudRepository<MediaAsset, UUID> {
    Flux<MediaAsset> findByUserId(UUID userId);
    Flux<MediaAsset> findByPostId(UUID postId);
    @Query("SELECT * FROM media_assets WHERE status = :status ORDER BY created_at DESC")
    Flux<MediaAsset> findByStatus(String status);
}
