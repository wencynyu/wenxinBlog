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
                    .then(postRepository.findById(postId).flatMap(post -> {
                        post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
                        return postRepository.save(post).thenReturn(false);
                    }));
            } else {
                return likeRepository.addLike(userId, postId)
                    .then(postRepository.findById(postId).flatMap(post -> {
                        post.setLikeCount(post.getLikeCount() + 1);
                        return postRepository.save(post).thenReturn(true);
                    }));
            }
        });
    }

    public Mono<Boolean> isLiked(UUID userId, UUID postId) {
        return likeRepository.existsByUserIdAndPostId(userId, postId).map(count -> count > 0);
    }
}
