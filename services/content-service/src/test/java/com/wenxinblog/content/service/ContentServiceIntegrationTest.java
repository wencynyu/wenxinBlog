package com.wenxinblog.content.service;

import com.wenxinblog.content.AbstractIntegrationTest;
import com.wenxinblog.content.entity.MediaAsset;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ContentService 集成测试：真实 MinIO round-trip（putObject/statObject/removeObject）
 * + 真实 PG（media_assets 行）。上传大小限制、所有权校验等分支在此验证。
 */
class ContentServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ContentService contentService;

    @Autowired
    private MinioClient minioClient;

    private final UUID userId = UUID.randomUUID();

    private FilePart mockFile(String filename, MediaType type, byte[] content) {
        FilePart file = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        when(file.filename()).thenReturn(filename);
        when(file.headers()).thenReturn(headers);
        when(file.content()).thenReturn(Flux.just(new DefaultDataBufferFactory().wrap(content)));
        return file;
    }

    private boolean objectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket("wenxinblog-content").object(objectKey).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void upload_image_persistsObjectToMinioAndRowToPg() {
        byte[] bytes = "fake-png-bytes".getBytes();

        StepVerifier.create(contentService.upload(userId, mockFile("cover.png", MediaType.IMAGE_PNG, bytes)))
                .assertNext(asset -> {
                    assertThat(asset.getId()).isNotNull();
                    assertThat(asset.getUserId()).isEqualTo(userId);
                    assertThat(asset.getType()).isEqualTo("IMAGE");
                    assertThat(asset.getStatus()).isEqualTo("READY");
                    assertThat(asset.getStorageProvider()).isEqualTo("MINIO");
                    assertThat(asset.getSizeBytes()).isEqualTo(bytes.length);
                    assertThat(asset.getCdnUrl()).contains("wenxinblog-content/uploads/" + userId);
                })
                .verifyComplete();

        // MinIO 里对象真实存在；PG 行可通过 getFile 读回
        assertThat(objectExists(objectKeyOf(userId))).isTrue();
        StepVerifier.create(contentService.getFile(assetIdOf(userId)))
                .assertNext(asset -> assertThat(asset.getOriginalFilename()).isEqualTo("cover.png"))
                .verifyComplete();
    }

    @Test
    void upload_videoMimeType_classifiedAsVideo() {
        StepVerifier.create(contentService.upload(userId, mockFile("clip.mp4", MediaType.valueOf("video/mp4"), "v".getBytes())))
                .assertNext(asset -> assertThat(asset.getType()).isEqualTo("VIDEO"))
                .verifyComplete();
    }

    @Test
    void upload_exceedingMaxSize_rejected400_withoutPersisting() throws Exception {
        // 把 50MB 限制临时调小到 4 字节（反射改 @Value 字段，避免再起一个上下文）
        ReflectionTestUtils.setField(contentService, "maxFileSize", DataSize.ofBytes(4));
        try {
            StepVerifier.create(contentService.upload(userId,
                            mockFile("big.bin", MediaType.APPLICATION_OCTET_STREAM, "12345678".getBytes())))
                    .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST))
                    .verify();

            assertThat(countAssetsOf(userId)).isZero(); // 超限文件不应落库
        } finally {
            ReflectionTestUtils.setField(contentService, "maxFileSize", DataSize.parse("50MB"));
        }
    }

    @Test
    void deleteFile_byOwner_removesObjectAndRow() {
        byte[] bytes = "to-delete".getBytes();
        UUID id = contentService.upload(userId, mockFile("del.png", MediaType.IMAGE_PNG, bytes)).block().getId();
        String objectKey = objectKeyOf(userId);
        assertThat(objectExists(objectKey)).isTrue();

        StepVerifier.create(contentService.deleteFile(userId, id)).verifyComplete();

        assertThat(objectExists(objectKey)).isFalse(); // MinIO 对象已删
        StepVerifier.create(contentService.getFile(id)).verifyComplete(); // PG 行已删
    }

    @Test
    void deleteFile_byNonOwner_rejected403_andNothingDeleted() {
        UUID id = contentService.upload(userId, mockFile("mine.png", MediaType.IMAGE_PNG, "x".getBytes())).block().getId();

        StepVerifier.create(contentService.deleteFile(UUID.randomUUID(), id))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();

        assertThat(objectExists(objectKeyOf(userId))).isTrue();
        StepVerifier.create(contentService.getFile(id))
                .assertNext(a -> assertThat(a.getId()).isEqualTo(id))
                .verifyComplete();
    }

    @Test
    void getFilesByPost_returnsOnlyThatPostsAssets() throws Exception {
        UUID postId = UUID.randomUUID();
        // 直接造行（upload 接口不设置 postId，关联由 blog-service 发帖时回填）
        try (var conn = openJdbc(); var st = conn.createStatement()) {
            st.execute("INSERT INTO media_assets (id, user_id, post_id, type, original_filename, mime_type, size_bytes, object_key, cdn_url, status, storage_provider) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + userId + "', '" + postId + "', 'IMAGE', 'a.png', 'image/png', 1, 'k1', 'u1', 'READY', 'MINIO')");
            st.execute("INSERT INTO media_assets (id, user_id, post_id, type, original_filename, mime_type, size_bytes, object_key, cdn_url, status, storage_provider) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + userId + "', '" + UUID.randomUUID() + "', 'IMAGE', 'b.png', 'image/png', 1, 'k2', 'u2', 'READY', 'MINIO')");
        }

        StepVerifier.create(contentService.getFilesByPost(postId).collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).getOriginalFilename()).isEqualTo("a.png");
                })
                .verifyComplete();
    }

    // ---- 工具：经 JDBC 取该用户的上传资产（每个用例前 truncate，userId 每用例独立）----

    private int countAssetsOf(UUID uid) {
        try (var conn = openJdbc(); var st = conn.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM media_assets WHERE user_id = '" + uid + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String objectKeyOf(UUID uid) {
        try (var conn = openJdbc(); var st = conn.createStatement();
             var rs = st.executeQuery("SELECT object_key FROM media_assets WHERE user_id = '" + uid + "'")) {
            rs.next();
            return rs.getString(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID assetIdOf(UUID uid) {
        try (var conn = openJdbc(); var st = conn.createStatement();
             var rs = st.executeQuery("SELECT id FROM media_assets WHERE user_id = '" + uid + "'")) {
            rs.next();
            return rs.getObject(1, UUID.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
