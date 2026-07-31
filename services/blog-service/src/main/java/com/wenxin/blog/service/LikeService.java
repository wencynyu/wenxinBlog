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
                return likeRepository.addLike(userId, postId)
                    .flatMap(inserted -> inserted > 0
                        ? postRepository.incrementLikeCount(postId).thenReturn(true)
                        : Mono.just(true));
            }
        });
    }

    public Mono<Boolean> isLiked(UUID userId, UUID postId) {
        return likeRepository.existsByUserIdAndPostId(userId, postId).map(count -> count > 0);
    }
}
