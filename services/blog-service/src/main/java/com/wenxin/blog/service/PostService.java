package com.wenxin.blog.service;

import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.dto.PostResponse;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.repository.PostLikeRepository;
import com.wenxin.blog.repository.PostRepository;
import com.wenxin.blog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;

    public Mono<Post> createPost(UUID authorId, PostRequest req) {
        Post post = new Post();
        post.setAuthorId(authorId);
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        post.setSummary(req.getSummary());
        post.setCoverImage(req.getCoverImage());
        post.setStatus(req.getStatus() != null ? req.getStatus() : "DRAFT");
        if ("PUBLISHED".equals(post.getStatus())) {
            post.setPublishedAt(LocalDateTime.now());
        }
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    public Mono<Post> updatePost(UUID id, PostRequest req) {
        return postRepository.findById(id).flatMap(post -> {
            if (req.getTitle() != null) post.setTitle(req.getTitle());
            if (req.getContent() != null) post.setContent(req.getContent());
            if (req.getSummary() != null) post.setSummary(req.getSummary());
            if (req.getCoverImage() != null) post.setCoverImage(req.getCoverImage());
            if (req.getStatus() != null) {
                post.setStatus(req.getStatus());
                if ("PUBLISHED".equals(req.getStatus()) && post.getPublishedAt() == null) {
                    post.setPublishedAt(LocalDateTime.now());
                }
            }
            post.setUpdatedAt(LocalDateTime.now());
            return postRepository.save(post);
        });
    }

    public Mono<Post> getPost(UUID id) {
        return postRepository.findById(id).flatMap(post -> {
            post.setViewCount(post.getViewCount() + 1);
            return postRepository.save(post);
        });
    }

    public Mono<Void> deletePost(UUID id) {
        return postRepository.deleteById(id);
    }

    public Flux<Post> listPublishedPosts(int page, int size) {
        return postRepository.findPublished(PageRequest.of(page, size));
    }

    public Flux<Post> listPostsByAuthor(UUID authorId, int page, int size) {
        return postRepository.findByAuthorId(authorId, PageRequest.of(page, size));
    }

    public Mono<Void> publishPost(UUID id) {
        return postRepository.findById(id).flatMap(post -> {
            post.setStatus("PUBLISHED");
            post.setPublishedAt(LocalDateTime.now());
            post.setUpdatedAt(LocalDateTime.now());
            return postRepository.save(post).then();
        });
    }
}
