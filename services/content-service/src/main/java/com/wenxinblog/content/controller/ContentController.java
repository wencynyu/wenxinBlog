package com.wenxinblog.content.controller;

import com.wenxinblog.content.dto.Result;
import com.wenxinblog.content.dto.UploadResponse;
import com.wenxinblog.content.entity.MediaAsset;
import com.wenxinblog.content.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @PostMapping("/upload")
    public Mono<Result<UploadResponse>> upload(@RequestHeader("X-User-Id") UUID userId,
                                               @RequestPart("file") FilePart file) {
        return contentService.upload(userId, file)
            .map(asset -> Result.success(UploadResponse.builder()
                .id(asset.getId()).objectKey(asset.getObjectKey())
                .cdnUrl(asset.getCdnUrl()).status(asset.getStatus())
                .createdAt(asset.getCreatedAt()).build()));
    }

    @GetMapping("/{id}")
    public Mono<Result<MediaAsset>> getFile(@PathVariable UUID id) {
        return contentService.getFile(id)
            .map(Result::success)
            .switchIfEmpty(Mono.just(Result.error(404, "File not found")));
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> deleteFile(@PathVariable UUID id) {
        return contentService.deleteFile(id).thenReturn(Result.success(null));
    }

    @GetMapping("/post/{postId}")
    public Mono<Result<List<MediaAsset>>> getFilesByPost(@PathVariable UUID postId) {
        return contentService.getFilesByPost(postId).collectList()
            .map(Result::success);
    }
}
