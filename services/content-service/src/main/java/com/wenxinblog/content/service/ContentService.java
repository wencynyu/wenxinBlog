package com.wenxinblog.content.service;

import com.wenxinblog.content.entity.MediaAsset;
import com.wenxinblog.content.repository.MediaAssetRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.unit.DataSize;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final MediaAssetRepository mediaRepo;
    private final MinioClient minioClient;

    @Value("${minio.bucket:wenxinblog-content}")
    private String bucket;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${file.upload.max-size:50MB}")
    private DataSize maxFileSize;

    /**
     * 上传文件到 MinIO + 保存 metadata 到 PG。
     * FilePart → byte[]（reactive read）→ MinIO putObject（boundedElastic，blocking offload）。
     */
    public Mono<MediaAsset> upload(UUID userId, FilePart file) {
        String objectKey = "uploads/" + userId + "/" + UUID.randomUUID() + "/" + file.filename();
        String mimeType = file.headers().getContentType() != null
                ? file.headers().getContentType().toString() : "application/octet-stream";
        String cdnUrl = endpoint + "/" + bucket + "/" + objectKey;
        String type = mimeType.startsWith("video/") ? "VIDEO" : "IMAGE";

        return DataBufferUtils.join(file.content())
                .map(this::toByteArray)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(bytes -> {
                    if (bytes.length > maxFileSize.toBytes()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "File size " + bytes.length + " exceeds limit " + maxFileSize));
                    }
                    // Upload to MinIO
                    try {
                        minioClient.putObject(PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1)
                                .contentType(mimeType)
                                .build());
                        log.info("Uploaded {} ({} bytes) → MinIO {}/{}", file.filename(), bytes.length, bucket, objectKey);
                    } catch (Exception e) {
                        log.error("MinIO upload failed for {}: {}", file.filename(), e.getMessage());
                        return Mono.<MediaAsset>error(e);
                    }
                    // Save metadata
                    MediaAsset asset = new MediaAsset();
                    asset.setUserId(userId);
                    asset.setType(type);
                    asset.setOriginalFilename(file.filename());
                    asset.setMimeType(mimeType);
                    asset.setSizeBytes((long) bytes.length);
                    asset.setObjectKey(objectKey);
                    asset.setCdnUrl(cdnUrl);
                    asset.setStatus("READY");
                    asset.setStorageProvider("MINIO");
                    asset.setCreatedAt(LocalDateTime.now());
                    asset.setUpdatedAt(LocalDateTime.now());
                    return mediaRepo.save(asset);
                });
    }

    public Mono<MediaAsset> getFile(UUID id) {
        return mediaRepo.findById(id);
    }

    public Mono<Void> deleteFile(UUID userId, UUID id) {
        return mediaRepo.findById(id)
                .switchIfEmpty(Mono.empty())
                .flatMap(asset -> {
                    if (!asset.getUserId().equals(userId)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner"));
                    }
                    return removeMinioObject(asset.getObjectKey())
                            .then(mediaRepo.deleteById(id));
                });
    }

    /** 删除 MinIO 对象（best-effort：失败仅记日志，不阻断 DB 行删除）。 */
    private Mono<Void> removeMinioObject(String objectKey) {
        return Mono.fromRunnable(() -> {
                    try {
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .build());
                        log.info("Removed MinIO object {}:{}", bucket, objectKey);
                    } catch (Exception e) {
                        log.error("MinIO removeObject failed for {}:{}: {}", bucket, objectKey, e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Flux<MediaAsset> getFilesByPost(UUID postId) {
        return mediaRepo.findByPostId(postId);
    }

    private byte[] toByteArray(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }
}
