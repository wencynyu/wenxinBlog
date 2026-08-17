package com.wenxinblog.recommendation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.repository.UserInterestTagRepository;
import com.wenxinblog.recommendation.service.RecommendationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BehaviorEventConsumerTest {

    @Mock
    private UserInterestTagRepository interestTagRepository;

    @Mock
    private RecommendationService recommendationService;

    @InjectMocks
    private BehaviorEventConsumer consumer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // consume() 末尾会触发 refreshUserVector（行为→画像闭环）；lenient 兜底为空向量，避免 NPE
        lenient().when(recommendationService.refreshUserVector(anyString())).thenReturn(Mono.empty());
        consumer = new BehaviorEventConsumer(objectMapper, interestTagRepository, recommendationService);
    }

    @Test
    void consume_WithValidEventAndTags_ShouldSaveInterestTag() {
        // Given
        String userId = "user-123";
        String tag = "java";

        // Create a real JSON structure
        String eventJson = String.format("{\"eventType\":\"like_post\",\"userId\":\"%s\",\"tags\":[\"%s\"]}", userId, tag);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());
        when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        ArgumentCaptor<UserInterestTag> captor = ArgumentCaptor.forClass(UserInterestTag.class);
        verify(interestTagRepository, after(100)).save(captor.capture());

        UserInterestTag savedTag = captor.getValue();
        assertEquals(userId, savedTag.getUserId());
        assertEquals(tag, savedTag.getTag());
        assertEquals(0.5, savedTag.getWeight()); // like_post weight
    }

    @Test
    void consume_WithCommentEvent_ShouldUseCorrectWeight() {
        // Given
        String userId = "user-456";
        String tag = "spring";
        String eventJson = String.format("{\"eventType\":\"comment_post\",\"userId\":\"%s\",\"tags\":[\"%s\"]}", userId, tag);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());
        when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        ArgumentCaptor<UserInterestTag> captor = ArgumentCaptor.forClass(UserInterestTag.class);
        verify(interestTagRepository, after(100)).save(captor.capture());

        assertEquals(0.7, captor.getValue().getWeight()); // comment_post weight
    }

    @Test
    void consume_WithShareEvent_ShouldUseCorrectWeight() {
        // Given
        String userId = "user-789";
        String tag = "kafka";
        String eventJson = String.format("{\"eventType\":\"share_post\",\"userId\":\"%s\",\"tags\":[\"%s\"]}", userId, tag);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());
        when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        ArgumentCaptor<UserInterestTag> captor = ArgumentCaptor.forClass(UserInterestTag.class);
        verify(interestTagRepository, after(100)).save(captor.capture());

        assertEquals(0.8, captor.getValue().getWeight()); // share_post weight
    }

    @Test
    void consume_WithViewEvent_ShouldUseCorrectWeight() {
        // Given
        String userId = "user-101";
        String tag = "redis";
        String eventJson = String.format("{\"eventType\":\"view_post\",\"userId\":\"%s\",\"tags\":[\"%s\"]}", userId, tag);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());
        when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        ArgumentCaptor<UserInterestTag> captor = ArgumentCaptor.forClass(UserInterestTag.class);
        verify(interestTagRepository, after(100)).save(captor.capture());

        assertEquals(0.1, captor.getValue().getWeight()); // view_post weight
    }

    @Test
    void consume_WithUnknownEventType_ShouldUseDefaultWeight() {
        // Given
        String userId = "user-202";
        String tag = "docker";
        String eventJson = String.format("{\"eventType\":\"unknown_event\",\"userId\":\"%s\",\"tags\":[\"%s\"]}", userId, tag);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());
        when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        ArgumentCaptor<UserInterestTag> captor = ArgumentCaptor.forClass(UserInterestTag.class);
        verify(interestTagRepository, after(100)).save(captor.capture());

        assertEquals(0.2, captor.getValue().getWeight()); // default weight
    }

    @Test
    void consume_WithoutTags_ShouldNotSave() {
        // Given
        String eventJson = "{\"eventType\":\"view_post\",\"userId\":\"user-303\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithDuplicateTag_ShouldNotSaveAgain() {
        // Given
        String userId = "user-404";
        String tag = "java";
        String eventJson = String.format("{\"eventType\":\"like_post\",\"userId\":\"%s\",\"tags\":[\"%s\"]}", userId, tag);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        // Tag already exists
        UserInterestTag existingTag = UserInterestTag.builder()
                .userId(userId).tag(tag).weight(0.5).createdAt(LocalDateTime.now()).build();
        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.just(existingTag));

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithInvalidJson_ShouldNotCrash() {
        // Given
        String eventJson = "invalid json";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        // When
        consumer.consume(record);

        // Then - should not throw exception
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithMultipleTags_ShouldProcessAll() {
        // Given
        String userId = "user-505";
        String eventJson = String.format("{\"eventType\":\"like_post\",\"userId\":\"%s\",\"tags\":[\"java\",\"spring\",\"kafka\"]}", userId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.empty());
        when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        verify(interestTagRepository, after(100).times(3)).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithMixedNewAndExistingTags_ShouldOnlySaveNew() {
        // Given
        String userId = "user-606";
        String eventJson = String.format("{\"eventType\":\"like_post\",\"userId\":\"%s\",\"tags\":[\"java\",\"spring\"]}", userId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        // "java" already exists
        UserInterestTag existingTag = UserInterestTag.builder()
                .userId(userId).tag("java").weight(0.5).createdAt(LocalDateTime.now()).build();
        when(interestTagRepository.findByUserId(userId)).thenReturn(Flux.just(existingTag));
        when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then - only "spring" should be saved
        verify(interestTagRepository, after(100).times(1)).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithEmptyTagsArray_ShouldNotSave() {
        // Given
        String eventJson = "{\"eventType\":\"view_post\",\"userId\":\"user-707\",\"tags\":[]}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        // When
        consumer.consume(record).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        // Then
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithNullEventValue_ShouldNotCrash() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", null);

        // When
        consumer.consume(record);

        // Then - should not throw exception
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithMissingEventType_ShouldNotCrash() {
        // Given
        String eventJson = "{\"userId\":\"user-808\",\"tags\":[\"java\"]}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        // When
        consumer.consume(record);

        // Then - should not throw exception
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    @Test
    void consume_WithMissingUserId_ShouldNotCrash() {
        // Given
        String eventJson = "{\"eventType\":\"like_post\",\"tags\":[\"java\"]}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson);

        // When
        consumer.consume(record);

        // Then - should not throw exception (will fail in repository)
        verify(interestTagRepository, never()).save(any(UserInterestTag.class));
    }

    // ---- 帖子向量 EMA / 已看记录 路径（item-CF 行为闭环）----

    @Test
    void consume_WithPostId_UpdatesUserVectorViaEma_AndRecordsViewed() {
        String userId = "user-ema";
        String postId = "11111111-1111-1111-1111-111111111111";
        lenient().when(interestTagRepository.findByUserId(anyString())).thenReturn(Flux.empty());
        lenient().when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());
        lenient().when(recommendationService.updateUserVectorWithPost(anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.empty());
        lenient().when(recommendationService.recordViewedPost(anyString(), anyString())).thenReturn(Mono.empty());

        // like_post 权重 0.5
        String eventJson = String.format(
                "{\"eventType\":\"like_post\",\"userId\":\"%s\",\"postId\":\"%s\",\"tags\":[\"java\"]}", userId, postId);
        consumer.consume(new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson)).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        verify(recommendationService, after(200)).updateUserVectorWithPost(userId, postId, 0.5);
        verify(recommendationService, after(200)).recordViewedPost(userId, postId);
    }

    @Test
    void consume_WithoutPostId_RefreshesVectorFromTagsOnly() {
        String userId = "user-tags-only";
        lenient().when(interestTagRepository.findByUserId(anyString())).thenReturn(Flux.empty());
        lenient().when(interestTagRepository.save(any(UserInterestTag.class))).thenReturn(Mono.empty());
        String eventJson = String.format(
                "{\"eventType\":\"view_post\",\"userId\":\"%s\",\"tags\":[\"go\"]}", userId);
        consumer.consume(new ConsumerRecord<>("user-behavior-events", 0, 0, "key", eventJson)).block(); // 订阅触发链路（fe5e1ec 起 consume 返回惰性 Mono，需订阅才执行）

        verify(recommendationService, after(200)).refreshUserVector(userId);
        verify(recommendationService, never()).updateUserVectorWithPost(anyString(), anyString(), anyDouble());
        verify(recommendationService, never()).recordViewedPost(anyString(), anyString());
    }
}
