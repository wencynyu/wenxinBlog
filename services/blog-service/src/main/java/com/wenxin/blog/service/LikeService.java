package com.wenxin.blog.service;

import com.wenxin.blog.repository.PostLikeRepository;
import com.wenxin.blog.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;

    public Mono<Boolean> toggleLike(UUID userId, UUID postId) {
        return likeRepository.existsByUserIdAndPostId(userId, postId).flatMap(exists -> {
            if (exists > 0) {
                return likeRepository.deleteByUserIdAndPostId(userId, postId)
                    .then(postRepository.decrementLikeCount(postId).thenReturn(false));
            } else {
                // 注意：addLike 的 @Query INSERT 在 R2DBC 下返回空 Mono（拿不到影响行数），
                // 不能依赖其值，统一用 then() 串行执行后再返回 true（与 FavoriteService 保持一致）。
                return likeRepository.addLike(userId, postId)
                    .then(postRepository.incrementLikeCount(postId).thenReturn(true));
            }
        });
    }

    public Mono<Boolean> isLiked(UUID userId, UUID postId) {
        return likeRepository.existsByUserIdAndPostId(userId, postId).map(count -> count > 0);
    }
}
