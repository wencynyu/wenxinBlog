package com.wenxinblog.content.repository;

import com.wenxinblog.content.entity.MediaVariant;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;

public interface MediaVariantRepository extends ReactiveCrudRepository<MediaVariant, UUID> {
    Flux<MediaVariant> findByAssetId(UUID assetId);
}
